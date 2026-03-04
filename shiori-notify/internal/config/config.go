package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/rs/zerolog"
)

const (
	defaultHTTPAddr          = ":8090"
	defaultRabbitMQAddr      = "amqp://localhost:5672/"
	defaultRabbitMQExchange  = "shiori.order.event"
	defaultRabbitMQExchanges = "shiori.order.event,shiori.user.event"
	defaultRabbitMQQueue     = "notify.order.event"
	defaultRabbitMQRouteKeys = "order.created,order.paid,order.canceled,order.delivered,order.finished,user.status.changed,user.role.changed,user.password.reset"
	defaultMetricsEnabled    = true
	defaultStoreDriver       = "memory"
	defaultStoreMaxPerUser   = 1000
	defaultReplayLimit       = 50
	defaultReplayMaxLimit    = 200
	defaultWSReplayLimit     = 100
	defaultAuthEnabled       = true
	defaultJWTIssuer         = "shiori"
	defaultMySQLMaxOpenConns = 20
	defaultMySQLMaxIdleConns = 10
	defaultWSPath            = "/ws"
	defaultChatEnabled       = false
	defaultChatPageLimit     = 20
	defaultChatMaxPageLimit  = 100
	defaultChatTicketIssuer  = "shiori-chat-ticket"
	defaultChatMQEnabled     = true
	defaultChatMQExchange    = "shiori.chat.event"
)

var (
	defaultWSWriteTimeout       = 5 * time.Second
	defaultWSPingInterval       = 30 * time.Second
	defaultMySQLConnMaxLifetime = 30 * time.Minute
)

type Config struct {
	HTTPAddr             string
	RabbitMQAddr         string
	RabbitMQExchange     string
	RabbitMQQueue        string
	RabbitMQRoutingKey   string
	RabbitMQExchanges    []string
	RabbitMQRoutingKeys  []string
	LogLevel             zerolog.Level
	WSWriteTimeout       time.Duration
	WSPingInterval       time.Duration
	MetricsEnabled       bool
	StoreDriver          string
	StoreMaxPerUser      int
	ReplayDefaultLimit   int
	ReplayMaxLimit       int
	WSReplayLimit        int
	AuthEnabled          bool
	JWTHmacSecret        string
	JWTIssuer            string
	MySQLDSN             string
	MySQLMaxOpenConns    int
	MySQLMaxIdleConns    int
	MySQLConnMaxLifetime time.Duration
	WSPath               string
	ChatEnabled          bool
	ChatDefaultLimit     int
	ChatMaxLimit         int
	ChatTicketIssuer     string
	ChatTicketPublicKey  string
	ChatMQEnabled        bool
	ChatMQExchange       string
	InstanceID           string
}

func Load() Config {
	exchanges := parseCSV(
		envOrDefault(
			"RABBITMQ_EXCHANGES",
			envOrDefault("RABBITMQ_EXCHANGE", defaultRabbitMQExchanges),
		),
	)
	if len(exchanges) == 0 {
		exchanges = []string{defaultRabbitMQExchange}
	}
	routingKeys := parseCSV(
		envOrDefault(
			"RABBITMQ_ROUTING_KEYS",
			envOrDefault("RABBITMQ_ROUTING_KEY", defaultRabbitMQRouteKeys),
		),
	)
	if len(routingKeys) == 0 {
		routingKeys = parseCSV(defaultRabbitMQRouteKeys)
	}

	return Config{
		HTTPAddr:             envOrDefault("NOTIFY_HTTP_ADDR", defaultHTTPAddr),
		RabbitMQAddr:         envOrDefault("RABBITMQ_ADDR", defaultRabbitMQAddr),
		RabbitMQExchange:     exchanges[0],
		RabbitMQQueue:        envOrDefault("RABBITMQ_QUEUE", defaultRabbitMQQueue),
		RabbitMQRoutingKey:   routingKeys[0],
		RabbitMQExchanges:    exchanges,
		RabbitMQRoutingKeys:  routingKeys,
		LogLevel:             parseLogLevel(envOrDefault("LOG_LEVEL", "info")),
		WSWriteTimeout:       parseDurationOrDefault("WS_WRITE_TIMEOUT", defaultWSWriteTimeout),
		WSPingInterval:       parseDurationOrDefault("WS_PING_INTERVAL", defaultWSPingInterval),
		MetricsEnabled:       parseBoolOrDefault("NOTIFY_METRICS_ENABLED", defaultMetricsEnabled),
		StoreDriver:          strings.ToLower(envOrDefault("NOTIFY_STORE_DRIVER", defaultStoreDriver)),
		StoreMaxPerUser:      parseIntOrDefault("NOTIFY_EVENT_STORE_MAX_PER_USER", defaultStoreMaxPerUser),
		ReplayDefaultLimit:   parseIntOrDefault("NOTIFY_REPLAY_DEFAULT_LIMIT", defaultReplayLimit),
		ReplayMaxLimit:       parseIntOrDefault("NOTIFY_REPLAY_MAX_LIMIT", defaultReplayMaxLimit),
		WSReplayLimit:        parseIntOrDefault("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", defaultWSReplayLimit),
		AuthEnabled:          parseBoolOrDefault("NOTIFY_AUTH_ENABLED", defaultAuthEnabled),
		JWTHmacSecret:        envOrDefault("NOTIFY_JWT_HMAC_SECRET", ""),
		JWTIssuer:            envOrDefault("NOTIFY_JWT_ISSUER", defaultJWTIssuer),
		MySQLDSN:             envOrDefault("NOTIFY_MYSQL_DSN", ""),
		MySQLMaxOpenConns:    parseIntOrDefault("NOTIFY_MYSQL_MAX_OPEN_CONNS", defaultMySQLMaxOpenConns),
		MySQLMaxIdleConns:    parseIntOrDefault("NOTIFY_MYSQL_MAX_IDLE_CONNS", defaultMySQLMaxIdleConns),
		MySQLConnMaxLifetime: parseDurationOrDefault("NOTIFY_MYSQL_CONN_MAX_LIFETIME", defaultMySQLConnMaxLifetime),
		WSPath:               envOrDefault("NOTIFY_WS_PATH", defaultWSPath),
		ChatEnabled:          parseBoolOrDefault("NOTIFY_CHAT_ENABLED", defaultChatEnabled),
		ChatDefaultLimit:     parseIntOrDefault("NOTIFY_CHAT_DEFAULT_LIMIT", defaultChatPageLimit),
		ChatMaxLimit:         parseIntOrDefault("NOTIFY_CHAT_MAX_LIMIT", defaultChatMaxPageLimit),
		ChatTicketIssuer:     envOrDefault("NOTIFY_CHAT_TICKET_ISSUER", defaultChatTicketIssuer),
		ChatTicketPublicKey:  envOrDefault("NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64", ""),
		ChatMQEnabled:        parseBoolOrDefault("NOTIFY_CHAT_MQ_ENABLED", defaultChatMQEnabled),
		ChatMQExchange:       envOrDefault("NOTIFY_CHAT_MQ_EXCHANGE", defaultChatMQExchange),
		InstanceID:           envOrDefault("NOTIFY_INSTANCE_ID", defaultInstanceID()),
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

func parseCSV(raw string) []string {
	if strings.TrimSpace(raw) == "" {
		return nil
	}
	parts := strings.Split(raw, ",")
	result := make([]string, 0, len(parts))
	seen := make(map[string]struct{}, len(parts))
	for _, part := range parts {
		normalized := strings.TrimSpace(part)
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

func defaultInstanceID() string {
	hostname, err := os.Hostname()
	if err != nil || strings.TrimSpace(hostname) == "" {
		hostname = "notify"
	}
	return fmt.Sprintf("%s-%d", hostname, os.Getpid())
}
