package notifylog

import (
	"os"
	"time"

	"github.com/rs/zerolog"
)

func New(level zerolog.Level) *zerolog.Logger {
	zerolog.TimeFieldFormat = time.RFC3339
	consoleWriter := zerolog.ConsoleWriter{
		Out:        os.Stdout,
		TimeFormat: "3:04PM",
		NoColor:    false,
		PartsOrder: []string{
			zerolog.TimestampFieldName,
			zerolog.LevelFieldName,
			zerolog.MessageFieldName,
		},
	}

	logger := zerolog.New(consoleWriter).
		Level(level).
		With().
		Timestamp().
		Str("component", "shiori-notify").
		Logger()
	return &logger
}
