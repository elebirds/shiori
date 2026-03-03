package router

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

var ErrMissingTargetUser = errors.New("order paid payload missing userId")

type Hub interface {
	SendToUser(userID string, payload []byte) (int, error)
}

type Router struct {
	hub    Hub
	logger *zerolog.Logger
}

type orderPaidPayload struct {
	UserID json.RawMessage `json:"userId"`
}

func New(hub Hub, logger *zerolog.Logger) *Router {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Router{hub: hub, logger: logger}
}

func (r *Router) Route(ctx context.Context, env event.Envelope) error {
	switch env.Type {
	case "OrderPaid":
		return r.routeOrderPaid(ctx, env)
	default:
		r.logger.Debug().
			Str("eventId", env.EventID).
			Str("type", env.Type).
			Msg("忽略未支持事件类型")
		return nil
	}
}

func (r *Router) routeOrderPaid(ctx context.Context, env event.Envelope) error {
	startedAt := time.Now()
	defer metrics.ObserveMQRouteDuration(env.Type, time.Since(startedAt))

	var payload orderPaidPayload
	if err := json.Unmarshal(env.Payload, &payload); err != nil {
		metrics.AddWSPush("invalid_payload", env.Type, 1)
		return fmt.Errorf("unmarshal order paid payload: %w", err)
	}
	userID, err := parseUserID(payload.UserID)
	if err != nil {
		metrics.AddWSPush("invalid_user", env.Type, 1)
		return fmt.Errorf("parse order paid userId: %w", err)
	}
	if userID == "" {
		metrics.AddWSPush("missing_user", env.Type, 1)
		return ErrMissingTargetUser
	}

	message, err := json.Marshal(env)
	if err != nil {
		return fmt.Errorf("marshal ws payload: %w", err)
	}

	sent, err := r.hub.SendToUser(userID, message)
	if err != nil {
		if errors.Is(err, ws.ErrNoSession) {
			metrics.AddWSPush("no_session", env.Type, 1)
			r.logger.Debug().
				Str("eventId", env.EventID).
				Str("userId", userID).
				Msg("目标用户无活跃 WebSocket 会话")
			return nil
		}
		metrics.AddWSPush("error", env.Type, 1)
		return fmt.Errorf("send ws message failed: %w", err)
	}
	metrics.AddWSPush("success", env.Type, sent)

	r.logger.Info().
		Str("eventId", env.EventID).
		Str("userId", userID).
		Int("sent", sent).
		Msg("订单支付事件路由完成")
	return nil
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
