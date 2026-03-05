package chat

import (
	"errors"
	"fmt"
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
