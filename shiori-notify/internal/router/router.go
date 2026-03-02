package router

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/hhm/shiori/shiori-notify/internal/event"
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
	UserID string `json:"userId"`
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
	var payload orderPaidPayload
	if err := json.Unmarshal(env.Payload, &payload); err != nil {
		return fmt.Errorf("unmarshal order paid payload: %w", err)
	}
	if payload.UserID == "" {
		return ErrMissingTargetUser
	}

	message, err := json.Marshal(env)
	if err != nil {
		return fmt.Errorf("marshal ws payload: %w", err)
	}

	sent, err := r.hub.SendToUser(payload.UserID, message)
	if err != nil {
		if errors.Is(err, ws.ErrNoSession) {
			r.logger.Debug().
				Str("eventId", env.EventID).
				Str("userId", payload.UserID).
				Msg("目标用户无活跃 WebSocket 会话")
			return nil
		}
		return fmt.Errorf("send ws message failed: %w", err)
	}

	r.logger.Info().
		Str("eventId", env.EventID).
		Str("userId", payload.UserID).
		Int("sent", sent).
		Msg("订单支付事件路由完成")
	return nil
}
