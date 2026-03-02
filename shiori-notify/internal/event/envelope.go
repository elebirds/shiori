package event

import (
	"encoding/json"
	"errors"
	"fmt"
)

var ErrInvalidEnvelope = errors.New("invalid event envelope")

type Envelope struct {
	EventID     string          `json:"eventId"`
	Type        string          `json:"type"`
	AggregateID string          `json:"aggregateId"`
	CreatedAt   string          `json:"createdAt"`
	Payload     json.RawMessage `json:"payload"`
}

func (e Envelope) Validate() error {
	if e.EventID == "" {
		return fmt.Errorf("%w: eventId is required", ErrInvalidEnvelope)
	}
	if e.Type == "" {
		return fmt.Errorf("%w: type is required", ErrInvalidEnvelope)
	}
	if e.AggregateID == "" {
		return fmt.Errorf("%w: aggregateId is required", ErrInvalidEnvelope)
	}
	if e.CreatedAt == "" {
		return fmt.Errorf("%w: createdAt is required", ErrInvalidEnvelope)
	}
	if len(e.Payload) == 0 {
		return fmt.Errorf("%w: payload is required", ErrInvalidEnvelope)
	}
	return nil
}
