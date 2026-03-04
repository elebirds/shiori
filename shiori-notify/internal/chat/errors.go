package chat

import "errors"

var (
	ErrInvalidArgument    = errors.New("invalid chat argument")
	ErrForbidden          = errors.New("chat access forbidden")
	ErrInvalidTicket      = errors.New("invalid chat ticket")
	ErrConversationAbsent = errors.New("conversation not found")
)
