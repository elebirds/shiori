package router

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"strings"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

var ErrMissingTargetUser = errors.New("event payload missing target user")

type Hub interface {
	SendToUser(userID string, payload []byte) (int, error)
	KickUser(userID string) int
}

type EventStore interface {
	Save(userID string, env event.Envelope) (bool, error)
}

type Router struct {
	hub        Hub
	eventStore EventStore
	storeType  string
	logger     *zerolog.Logger
}

type orderPaidPayload struct {
	UserID json.RawMessage `json:"userId"`
}

type orderCreatedPayload struct {
	BuyerUserID  json.RawMessage `json:"buyerUserId"`
	SellerUserID json.RawMessage `json:"sellerUserId"`
}

type orderCanceledPayload struct {
	BuyerUserID  json.RawMessage `json:"buyerUserId"`
	SellerUserID json.RawMessage `json:"sellerUserId"`
}

type orderLifecyclePayload struct {
	BuyerUserID  json.RawMessage `json:"buyerUserId"`
	SellerUserID json.RawMessage `json:"sellerUserId"`
}

type userGovernancePayload struct {
	TargetUserID json.RawMessage `json:"targetUserId"`
	UserID       json.RawMessage `json:"userId"`
}

func New(hub Hub, eventStore EventStore, logger *zerolog.Logger) *Router {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Router{
		hub:        hub,
		eventStore: eventStore,
		storeType:  detectStoreType(eventStore),
		logger:     logger,
	}
}

func (r *Router) Route(ctx context.Context, env event.Envelope) error {
	startedAt := time.Now()
	defer metrics.ObserveMQRouteDuration(env.Type, time.Since(startedAt))

	targetUserIDs, err := r.extractTargetUserIDs(env)
	if err != nil {
		metrics.AddWSPush("invalid_payload", env.Type, 1)
		return err
	}
	if targetUserIDs == nil {
		return nil
	}
	if len(targetUserIDs) == 0 {
		metrics.AddWSPush("missing_user", env.Type, 1)
		return ErrMissingTargetUser
	}

	return r.routeToUsers(ctx, env, targetUserIDs)
}

func (r *Router) extractTargetUserIDs(env event.Envelope) ([]string, error) {
	switch env.Type {
	case "OrderCreated":
		var payload orderCreatedPayload
		if err := json.Unmarshal(env.Payload, &payload); err != nil {
			return nil, fmt.Errorf("unmarshal order created payload: %w", err)
		}
		return parseUserIDs(payload.BuyerUserID, payload.SellerUserID)
	case "OrderPaid":
		var payload orderPaidPayload
		if err := json.Unmarshal(env.Payload, &payload); err != nil {
			return nil, fmt.Errorf("unmarshal order paid payload: %w", err)
		}
		userID, err := parseUserID(payload.UserID)
		if err != nil {
			return nil, fmt.Errorf("parse order paid userId: %w", err)
		}
		return uniqueUserIDs(userID), nil
	case "OrderCanceled":
		var payload orderCanceledPayload
		if err := json.Unmarshal(env.Payload, &payload); err != nil {
			return nil, fmt.Errorf("unmarshal order canceled payload: %w", err)
		}
		return parseUserIDs(payload.BuyerUserID, payload.SellerUserID)
	case "OrderDelivered", "OrderFinished":
		var payload orderLifecyclePayload
		if err := json.Unmarshal(env.Payload, &payload); err != nil {
			return nil, fmt.Errorf("unmarshal order lifecycle payload: %w", err)
		}
		return parseUserIDs(payload.BuyerUserID, payload.SellerUserID)
	case "UserStatusChanged", "UserRoleChanged", "UserPasswordReset", "UserPermissionOverrideChanged", "UserRoleBindingsChanged":
		var payload userGovernancePayload
		if err := json.Unmarshal(env.Payload, &payload); err != nil {
			return nil, fmt.Errorf("unmarshal governance payload: %w", err)
		}
		userID, err := parseUserID(payload.TargetUserID)
		if err != nil {
			return nil, fmt.Errorf("parse targetUserId: %w", err)
		}
		if strings.TrimSpace(userID) == "" {
			userID, err = parseUserID(payload.UserID)
			if err != nil {
				return nil, fmt.Errorf("parse userId: %w", err)
			}
		}
		return uniqueUserIDs(userID), nil
	default:
		r.logger.Debug().
			Str("eventId", env.EventID).
			Str("type", env.Type).
			Msg("忽略未支持事件类型")
		return nil, nil
	}
}

func (r *Router) routeToUsers(_ context.Context, env event.Envelope, targetUserIDs []string) error {
	if isAuthzChangeEvent(env.Type) {
		totalKicked := 0
		for _, userID := range targetUserIDs {
			totalKicked += r.hub.KickUser(userID)
		}
		if totalKicked > 0 {
			metrics.AddNotifyWSKick(totalKicked)
		}
		r.logger.Info().
			Str("eventId", env.EventID).
			Str("type", env.Type).
			Int("targets", len(targetUserIDs)).
			Int("kicked", totalKicked).
			Msg("权限变更事件处理完成，已执行 websocket 踢线")
		return nil
	}

	message, err := json.Marshal(env)
	if err != nil {
		return fmt.Errorf("marshal ws payload: %w", err)
	}

	totalSent := 0
	noSessionUsers := 0
	for _, userID := range targetUserIDs {
		if r.eventStore != nil {
			saved, saveErr := r.eventStore.Save(userID, env)
			if saveErr != nil {
				metrics.IncStoreWrite(r.storeType, "error")
				metrics.AddWSPush("store_error", env.Type, 1)
				return fmt.Errorf("store event failed for user=%s: %w", userID, saveErr)
			}
			if !saved {
				metrics.IncStoreWrite(r.storeType, "deduplicated")
				metrics.AddWSPush("deduplicated", env.Type, 1)
				r.logger.Debug().
					Str("eventId", env.EventID).
					Str("userId", userID).
					Msg("检测到重复事件，跳过重复推送")
				continue
			}
			metrics.IncStoreWrite(r.storeType, "success")
		}

		sent, sendErr := r.hub.SendToUser(userID, message)
		if sendErr != nil {
			if errors.Is(sendErr, ws.ErrNoSession) {
				noSessionUsers++
				metrics.AddWSPush("no_session", env.Type, 1)
				continue
			}
			metrics.AddWSPush("error", env.Type, 1)
			return fmt.Errorf("send ws message failed for user=%s: %w", userID, sendErr)
		}
		totalSent += sent
		if sent > 0 {
			metrics.AddWSPush("success", env.Type, sent)
		}
	}

	r.logger.Info().
		Str("eventId", env.EventID).
		Str("type", env.Type).
		Int("targets", len(targetUserIDs)).
		Int("sent", totalSent).
		Int("noSessionUsers", noSessionUsers).
		Msg("通知事件路由完成")

	return nil
}

func parseUserIDs(rawIDs ...json.RawMessage) ([]string, error) {
	ids := make([]string, 0, len(rawIDs))
	for _, rawID := range rawIDs {
		userID, err := parseUserID(rawID)
		if err != nil {
			return nil, err
		}
		ids = append(ids, userID)
	}
	return uniqueUserIDs(ids...), nil
}

func uniqueUserIDs(ids ...string) []string {
	result := make([]string, 0, len(ids))
	seen := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		normalized := strings.TrimSpace(id)
		if normalized == "" {
			continue
		}
		if _, exists := seen[normalized]; exists {
			continue
		}
		seen[normalized] = struct{}{}
		result = append(result, normalized)
	}
	return result
}

func parseUserID(raw json.RawMessage) (string, error) {
	if len(raw) == 0 {
		return "", nil
	}

	var asString string
	if err := json.Unmarshal(raw, &asString); err == nil {
		return strings.TrimSpace(asString), nil
	}

	var asNumber json.Number
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	if err := decoder.Decode(&asNumber); err == nil {
		return strings.TrimSpace(asNumber.String()), nil
	}

	return "", errors.New("userId must be string or number")
}

func detectStoreType(eventStore EventStore) string {
	if eventStore == nil {
		return "none"
	}
	typ := reflect.TypeOf(eventStore)
	if typ == nil {
		return "unknown"
	}
	name := typ.String()
	lower := strings.ToLower(name)
	switch {
	case strings.Contains(lower, "memory"):
		return "memory"
	case strings.Contains(lower, "mysql"):
		return "mysql"
	default:
		return "unknown"
	}
}

func isAuthzChangeEvent(eventType string) bool {
	switch strings.TrimSpace(eventType) {
	case "UserPermissionOverrideChanged", "UserRoleBindingsChanged":
		return true
	default:
		return false
	}
}
