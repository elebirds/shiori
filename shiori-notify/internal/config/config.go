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
	defaultNacosAddr                 = "nacos:8848"
	defaultNacosConfigGroup          = "SHIORI_DEV_DOCKER"
	defaultHTTPAddr                  = ":8090"
	defaultRabbitMQExchange          = "shiori.order.event"
	defaultRabbitMQExchanges         = "shiori.order.event,shiori.user.event"
	defaultRabbitMQQueue             = "notify.order.event"
	defaultRabbitMQRouteKeys         = "order.created,order.paid,order.canceled,order.delivered,order.finished,user.status.changed,user.role.changed,user.password.reset"
	defaultMetricsEnabled            = true
	defaultStoreMaxPerUser           = 1000
	defaultReplayLimit               = 50
	defaultReplayMaxLimit            = 200
	defaultWSReplayLimit             = 100
	defaultMySQLMaxOpenConns         = 20
	defaultMySQLMaxIdleConns         = 10
	defaultWSPath                    = "/ws"
	defaultChatPageLimit             = 20
	defaultChatMaxPageLimit          = 100
	defaultChatTicketIssuer          = "shiori-chat-ticket"
	defaultChatMQEnabled             = true
	defaultChatMQExchange            = "shiori.chat.event"
	defaultJWTIssuer                 = "shiori"
	defaultGatewaySignMaxSkewSeconds = 300
)

var (
	defaultWSWriteTimeout       = 5 * time.Second
	defaultWSPingInterval       = 30 * time.Second
	defaultMySQLConnMaxLifetime = 30 * time.Minute
)

type NacosConnConfig struct {
	Addr      string
	Username  string
	Password  string
	Group     string
	Namespace string
}

type Config struct {
	HTTPAddr                  string
	RabbitMQAddr              string
	RabbitMQExchange          string
	RabbitMQQueue             string
	RabbitMQRoutingKey        string
	RabbitMQExchanges         []string
	RabbitMQRoutingKeys       []string
	LogLevel                  zerolog.Level
	WSWriteTimeout            time.Duration
	WSPingInterval            time.Duration
	MetricsEnabled            bool
	StoreDriver               string
	StoreMaxPerUser           int
	ReplayDefaultLimit        int
	ReplayMaxLimit            int
	WSReplayLimit             int
	AuthEnabled               bool
	JWTHmacSecret             string
	JWTIssuer                 string
	MySQLDSN                  string
	MySQLMaxOpenConns         int
	MySQLMaxIdleConns         int
	MySQLConnMaxLifetime      time.Duration
	WSPath                    string
	ChatEnabled               bool
	ChatDefaultLimit          int
	ChatMaxLimit              int
	ChatTicketIssuer          string
	ChatTicketPublicKey       string
	ChatMQEnabled             bool
	ChatMQExchange            string
	InstanceID                string
	GatewaySignSecret         string
	GatewaySignMaxSkewSeconds int64
}

type NotifyNacosConfig struct {
	Notify   notifySection   `yaml:"notify"`
	Security securitySection `yaml:"security"`
}

type notifySection struct {
	HTTP     notifyHTTPSection     `yaml:"http"`
	RabbitMQ notifyRabbitMQSection `yaml:"rabbitmq"`
	Log      notifyLogSection      `yaml:"log"`
	WS       notifyWSSection       `yaml:"ws"`
	Metrics  notifyMetricsSection  `yaml:"metrics"`
	Store    notifyStoreSection    `yaml:"store"`
	Replay   notifyReplaySection   `yaml:"replay"`
	Auth     notifyAuthSection     `yaml:"auth"`
	MySQL    notifyMySQLSection    `yaml:"mysql"`
	Chat     notifyChatSection     `yaml:"chat"`
	Instance notifyInstanceSection `yaml:"instance"`
}

type notifyHTTPSection struct {
	Addr string `yaml:"addr"`
}

type notifyRabbitMQSection struct {
	Addr        string `yaml:"addr"`
	Exchange    string `yaml:"exchange"`
	Queue       string `yaml:"queue"`
	RoutingKey  string `yaml:"routing-key"`
	Exchanges   string `yaml:"exchanges"`
	RoutingKeys string `yaml:"routing-keys"`
}

type notifyLogSection struct {
	Level string `yaml:"level"`
}

type notifyWSSection struct {
	Path         string `yaml:"path"`
	WriteTimeout string `yaml:"write-timeout"`
	PingInterval string `yaml:"ping-interval"`
}

type notifyMetricsSection struct {
	Enabled *bool `yaml:"enabled"`
}

type notifyStoreSection struct {
	Driver     string `yaml:"driver"`
	MaxPerUser int    `yaml:"max-per-user"`
}

type notifyReplaySection struct {
	DefaultLimit int `yaml:"default-limit"`
	MaxLimit     int `yaml:"max-limit"`
	WSLimit      int `yaml:"ws-limit"`
}

type notifyAuthSection struct {
	Enabled *bool `yaml:"enabled"`
}

type notifyMySQLSection struct {
	DSN             string `yaml:"dsn"`
	MaxOpenConns    int    `yaml:"max-open-conns"`
	MaxIdleConns    int    `yaml:"max-idle-conns"`
	ConnMaxLifetime string `yaml:"conn-max-lifetime"`
}

type notifyChatSection struct {
	Enabled         *bool  `yaml:"enabled"`
	DefaultLimit    int    `yaml:"default-limit"`
	MaxLimit        int    `yaml:"max-limit"`
	TicketIssuer    string `yaml:"ticket-issuer"`
	TicketPublicKey string `yaml:"ticket-public-key-pem-base64"`
	MQEnabled       *bool  `yaml:"mq-enabled"`
	MQExchange      string `yaml:"mq-exchange"`
}

type notifyInstanceSection struct {
	ID string `yaml:"id"`
}

type securitySection struct {
	JWT         securityJWTSection         `yaml:"jwt"`
	GatewaySign securityGatewaySignSection `yaml:"gateway-sign"`
}

type securityJWTSection struct {
	Issuer     string `yaml:"issuer"`
	HMACSecret string `yaml:"hmac-secret"`
}

type securityGatewaySignSection struct {
	InternalSecret string `yaml:"internal-secret"`
	MaxSkewSeconds int64  `yaml:"max-skew-seconds"`
}

func LoadNacosConnFromEnv() (NacosConnConfig, error) {
	cfg := NacosConnConfig{
		Addr:      envOrDefault("NACOS_ADDR", defaultNacosAddr),
		Username:  strings.TrimSpace(os.Getenv("NACOS_USERNAME")),
		Password:  strings.TrimSpace(os.Getenv("NACOS_PASSWORD")),
		Group:     envOrDefault("NACOS_CONFIG_GROUP", defaultNacosConfigGroup),
		Namespace: strings.TrimSpace(os.Getenv("NACOS_CONFIG_NAMESPACE")),
	}
	if cfg.Username == "" {
		return NacosConnConfig{}, fmt.Errorf("NACOS_USERNAME is required")
	}
	if cfg.Password == "" {
		return NacosConnConfig{}, fmt.Errorf("NACOS_PASSWORD is required")
	}
	if strings.TrimSpace(cfg.Addr) == "" {
		return NacosConnConfig{}, fmt.Errorf("NACOS_ADDR is required")
	}
	if strings.TrimSpace(cfg.Group) == "" {
		return NacosConnConfig{}, fmt.Errorf("NACOS_CONFIG_GROUP is required")
	}
	return cfg, nil
}

func (c NotifyNacosConfig) ToRuntimeConfig() (Config, error) {
	exchanges := parseCSV(strings.TrimSpace(c.Notify.RabbitMQ.Exchanges))
	if len(exchanges) == 0 {
		exchanges = parseCSV(defaultRabbitMQExchanges)
	}
	routingKeys := parseCSV(strings.TrimSpace(c.Notify.RabbitMQ.RoutingKeys))
	if len(routingKeys) == 0 {
		routingKeys = parseCSV(defaultRabbitMQRouteKeys)
	}
	cfg := Config{
		HTTPAddr:                  stringOrDefault(c.Notify.HTTP.Addr, defaultHTTPAddr),
		RabbitMQAddr:              strings.TrimSpace(c.Notify.RabbitMQ.Addr),
		RabbitMQExchange:          stringOrDefault(c.Notify.RabbitMQ.Exchange, firstOrDefault(exchanges, defaultRabbitMQExchange)),
		RabbitMQQueue:             stringOrDefault(c.Notify.RabbitMQ.Queue, defaultRabbitMQQueue),
		RabbitMQRoutingKey:        stringOrDefault(c.Notify.RabbitMQ.RoutingKey, firstOrDefault(routingKeys, "")),
		RabbitMQExchanges:         exchanges,
		RabbitMQRoutingKeys:       routingKeys,
		LogLevel:                  parseLogLevel(stringOrDefault(c.Notify.Log.Level, "info")),
		WSWriteTimeout:            parseDurationOrDefaultValue(c.Notify.WS.WriteTimeout, defaultWSWriteTimeout),
		WSPingInterval:            parseDurationOrDefaultValue(c.Notify.WS.PingInterval, defaultWSPingInterval),
		MetricsEnabled:            boolOrDefault(c.Notify.Metrics.Enabled, defaultMetricsEnabled),
		StoreDriver:               strings.ToLower(strings.TrimSpace(c.Notify.Store.Driver)),
		StoreMaxPerUser:           intOrDefault(c.Notify.Store.MaxPerUser, defaultStoreMaxPerUser),
		ReplayDefaultLimit:        intOrDefault(c.Notify.Replay.DefaultLimit, defaultReplayLimit),
		ReplayMaxLimit:            intOrDefault(c.Notify.Replay.MaxLimit, defaultReplayMaxLimit),
		WSReplayLimit:             intOrDefault(c.Notify.Replay.WSLimit, defaultWSReplayLimit),
		AuthEnabled:               boolOrDefault(c.Notify.Auth.Enabled, true),
		JWTHmacSecret:             strings.TrimSpace(c.Security.JWT.HMACSecret),
		JWTIssuer:                 stringOrDefault(c.Security.JWT.Issuer, defaultJWTIssuer),
		MySQLDSN:                  strings.TrimSpace(c.Notify.MySQL.DSN),
		MySQLMaxOpenConns:         intOrDefault(c.Notify.MySQL.MaxOpenConns, defaultMySQLMaxOpenConns),
		MySQLMaxIdleConns:         intOrDefault(c.Notify.MySQL.MaxIdleConns, defaultMySQLMaxIdleConns),
		MySQLConnMaxLifetime:      parseDurationOrDefaultValue(c.Notify.MySQL.ConnMaxLifetime, defaultMySQLConnMaxLifetime),
		WSPath:                    stringOrDefault(c.Notify.WS.Path, defaultWSPath),
		ChatEnabled:               boolOrDefault(c.Notify.Chat.Enabled, false),
		ChatDefaultLimit:          intOrDefault(c.Notify.Chat.DefaultLimit, defaultChatPageLimit),
		ChatMaxLimit:              intOrDefault(c.Notify.Chat.MaxLimit, defaultChatMaxPageLimit),
		ChatTicketIssuer:          stringOrDefault(c.Notify.Chat.TicketIssuer, defaultChatTicketIssuer),
		ChatTicketPublicKey:       strings.TrimSpace(c.Notify.Chat.TicketPublicKey),
		ChatMQEnabled:             boolOrDefault(c.Notify.Chat.MQEnabled, defaultChatMQEnabled),
		ChatMQExchange:            stringOrDefault(c.Notify.Chat.MQExchange, defaultChatMQExchange),
		InstanceID:                strings.TrimSpace(c.Notify.Instance.ID),
		GatewaySignSecret:         strings.TrimSpace(c.Security.GatewaySign.InternalSecret),
		GatewaySignMaxSkewSeconds: int64OrDefault(c.Security.GatewaySign.MaxSkewSeconds, defaultGatewaySignMaxSkewSeconds),
	}
	if cfg.InstanceID == "" {
		cfg.InstanceID = defaultInstanceID()
	}
	if err := validateRuntimeConfig(cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func validateRuntimeConfig(cfg Config) error {
	if strings.TrimSpace(cfg.HTTPAddr) == "" {
		return fmt.Errorf("notify.http.addr is required")
	}
	if strings.TrimSpace(cfg.RabbitMQAddr) == "" {
		return fmt.Errorf("notify.rabbitmq.addr is required")
	}
	if len(cfg.RabbitMQExchanges) == 0 {
		return fmt.Errorf("notify.rabbitmq.exchanges is required")
	}
	if len(cfg.RabbitMQRoutingKeys) == 0 {
		return fmt.Errorf("notify.rabbitmq.routing-keys is required")
	}
	if strings.TrimSpace(cfg.RabbitMQQueue) == "" {
		return fmt.Errorf("notify.rabbitmq.queue is required")
	}
	if strings.TrimSpace(cfg.StoreDriver) == "" {
		return fmt.Errorf("notify.store.driver is required")
	}
	if cfg.StoreMaxPerUser <= 0 {
		return fmt.Errorf("notify.store.max-per-user must be positive")
	}
	if cfg.ReplayDefaultLimit <= 0 || cfg.ReplayMaxLimit <= 0 || cfg.WSReplayLimit <= 0 {
		return fmt.Errorf("notify replay limits must be positive")
	}
	if cfg.AuthEnabled {
		if strings.TrimSpace(cfg.JWTIssuer) == "" {
			return fmt.Errorf("notify.security.jwt.issuer is required when auth enabled")
		}
		if strings.TrimSpace(cfg.JWTHmacSecret) == "" {
			return fmt.Errorf("notify.security.jwt.hmac-secret is required when auth enabled")
		}
	}
	if cfg.StoreDriver == "mysql" && strings.TrimSpace(cfg.MySQLDSN) == "" {
		return fmt.Errorf("notify.mysql.dsn is required when store.driver=mysql")
	}
	if cfg.ChatEnabled {
		if cfg.StoreDriver != "mysql" {
			return fmt.Errorf("chat requires mysql store driver")
		}
		if strings.TrimSpace(cfg.ChatTicketIssuer) == "" {
			return fmt.Errorf("notify.chat.ticket-issuer is required when chat enabled")
		}
		if strings.TrimSpace(cfg.ChatTicketPublicKey) == "" {
			return fmt.Errorf("notify.chat.ticket-public-key-pem-base64 is required when chat enabled")
		}
	}
	return nil
}

func firstOrDefault(items []string, fallback string) string {
	if len(items) == 0 {
		return fallback
	}
	return items[0]
}

func stringOrDefault(value, fallback string) string {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return fallback
	}
	return trimmed
}

func boolOrDefault(value *bool, fallback bool) bool {
	if value == nil {
		return fallback
	}
	return *value
}

func intOrDefault(value, fallback int) int {
	if value <= 0 {
		return fallback
	}
	return value
}

func int64OrDefault(value, fallback int64) int64 {
	if value <= 0 {
		return fallback
	}
	return value
}

func envOrDefault(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func parseDurationOrDefaultValue(raw string, fallback time.Duration) time.Duration {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return fallback
	}
	duration, err := time.ParseDuration(trimmed)
	if err != nil || duration <= 0 {
		return fallback
	}
	return duration
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
