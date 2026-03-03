package config

import (
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/rs/zerolog"
)

const (
	defaultHTTPAddr         = ":8090"
	defaultRabbitMQAddr     = "amqp://localhost:5672/"
	defaultRabbitMQExchange = "shiori.order.event"
	defaultRabbitMQQueue    = "notify.order.paid"
	defaultRabbitMQRouteKey = "order.paid"
	defaultMetricsEnabled   = true
	defaultStoreMaxPerUser  = 1000
	defaultReplayLimit      = 50
	defaultReplayMaxLimit   = 200
	defaultWSReplayLimit    = 100
)

var (
	defaultWSWriteTimeout = 5 * time.Second
	defaultWSPingInterval = 30 * time.Second
)

type Config struct {
	HTTPAddr           string
	RabbitMQAddr       string
	RabbitMQExchange   string
	RabbitMQQueue      string
	RabbitMQRoutingKey string
	LogLevel           zerolog.Level
	WSWriteTimeout     time.Duration
	WSPingInterval     time.Duration
	MetricsEnabled     bool
	StoreMaxPerUser    int
	ReplayDefaultLimit int
	ReplayMaxLimit     int
	WSReplayLimit      int
}

func Load() Config {
	return Config{
		HTTPAddr:           envOrDefault("NOTIFY_HTTP_ADDR", defaultHTTPAddr),
		RabbitMQAddr:       envOrDefault("RABBITMQ_ADDR", defaultRabbitMQAddr),
		RabbitMQExchange:   envOrDefault("RABBITMQ_EXCHANGE", defaultRabbitMQExchange),
		RabbitMQQueue:      envOrDefault("RABBITMQ_QUEUE", defaultRabbitMQQueue),
		RabbitMQRoutingKey: envOrDefault("RABBITMQ_ROUTING_KEY", defaultRabbitMQRouteKey),
		LogLevel:           parseLogLevel(envOrDefault("LOG_LEVEL", "info")),
		WSWriteTimeout:     parseDurationOrDefault("WS_WRITE_TIMEOUT", defaultWSWriteTimeout),
		WSPingInterval:     parseDurationOrDefault("WS_PING_INTERVAL", defaultWSPingInterval),
		MetricsEnabled:     parseBoolOrDefault("NOTIFY_METRICS_ENABLED", defaultMetricsEnabled),
		StoreMaxPerUser:    parseIntOrDefault("NOTIFY_EVENT_STORE_MAX_PER_USER", defaultStoreMaxPerUser),
		ReplayDefaultLimit: parseIntOrDefault("NOTIFY_REPLAY_DEFAULT_LIMIT", defaultReplayLimit),
		ReplayMaxLimit:     parseIntOrDefault("NOTIFY_REPLAY_MAX_LIMIT", defaultReplayMaxLimit),
		WSReplayLimit:      parseIntOrDefault("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", defaultWSReplayLimit),
	}
}

func envOrDefault(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func parseDurationOrDefault(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	duration, err := time.ParseDuration(value)
	if err != nil || duration <= 0 {
		return fallback
	}
	return duration
}

func parseLogLevel(value string) zerolog.Level {
	levelText := strings.ToLower(strings.TrimSpace(value))
	if levelText == "warning" {
		levelText = "warn"
	}
	level, err := zerolog.ParseLevel(levelText)
	if err != nil {
		return zerolog.InfoLevel
	}
	return level
}

func parseBoolOrDefault(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func parseIntOrDefault(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}
