package chat

import (
	"errors"
	"fmt"
	"strings"
)

var (
	ErrInvalidArgument    = errors.New("invalid chat argument")
	ErrForbidden          = errors.New("chat access forbidden")
	ErrInvalidTicket      = errors.New("invalid chat ticket")
	ErrConversationAbsent = errors.New("conversation not found")
	ErrReportAbsent       = errors.New("chat report not found")
	ErrForbiddenWordRule  = errors.New("chat forbidden word rule not found")
	ErrBlocked            = errors.New("chat blocked between users")
	ErrForbiddenWord      = errors.New("chat message contains forbidden content")
)

type ErrRateLimited struct {
	RetryAfterSeconds int
}

func (e *ErrRateLimited) Error() string {
	return fmt.Sprintf("chat rate limited, retry after %ds", e.RetryAfterSeconds)
}

type ErrCapabilityBanned struct {
	Capability string
}

func (e *ErrCapabilityBanned) Error() string {
	capability := strings.TrimSpace(strings.ToUpper(e.Capability))
	if capability == "" {
		capability = "UNKNOWN"
	}
	return fmt.Sprintf("chat capability banned: %s", capability)
}

type ErrCapabilityCheckFailed struct {
	Reason     string
	StatusCode int
	Cause      error
}

func (e *ErrCapabilityCheckFailed) Error() string {
	reason := strings.TrimSpace(strings.ToLower(e.Reason))
	if reason == "" {
		reason = "unknown"
	}
	if e.StatusCode > 0 {
		return fmt.Sprintf("chat capability check failed: reason=%s,status=%d", reason, e.StatusCode)
	}
	return fmt.Sprintf("chat capability check failed: reason=%s", reason)
}

func (e *ErrCapabilityCheckFailed) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.Cause
}
