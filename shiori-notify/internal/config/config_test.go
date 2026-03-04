package config

import (
	"testing"
	"time"

	"github.com/rs/zerolog"
)

func TestLoadDefaults(t *testing.T) {
	t.Setenv("NOTIFY_HTTP_ADDR", "")
	t.Setenv("RABBITMQ_ADDR", "")
	t.Setenv("RABBITMQ_EXCHANGE", "")
	t.Setenv("RABBITMQ_QUEUE", "")
	t.Setenv("RABBITMQ_ROUTING_KEY", "")
	t.Setenv("RABBITMQ_EXCHANGES", "")
	t.Setenv("RABBITMQ_ROUTING_KEYS", "")
	t.Setenv("LOG_LEVEL", "")
	t.Setenv("WS_WRITE_TIMEOUT", "")
	t.Setenv("WS_PING_INTERVAL", "")
	t.Setenv("NOTIFY_METRICS_ENABLED", "")
	t.Setenv("NOTIFY_STORE_DRIVER", "")
	t.Setenv("NOTIFY_EVENT_STORE_MAX_PER_USER", "")
	t.Setenv("NOTIFY_REPLAY_DEFAULT_LIMIT", "")
	t.Setenv("NOTIFY_REPLAY_MAX_LIMIT", "")
	t.Setenv("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", "")
	t.Setenv("NOTIFY_AUTH_ENABLED", "")
	t.Setenv("NOTIFY_JWT_HMAC_SECRET", "")
	t.Setenv("NOTIFY_JWT_ISSUER", "")
	t.Setenv("NOTIFY_MYSQL_DSN", "")
	t.Setenv("NOTIFY_MYSQL_MAX_OPEN_CONNS", "")
	t.Setenv("NOTIFY_MYSQL_MAX_IDLE_CONNS", "")
	t.Setenv("NOTIFY_MYSQL_CONN_MAX_LIFETIME", "")

	cfg := Load()
	if cfg.HTTPAddr != defaultHTTPAddr {
		t.Fatalf("unexpected default HTTPAddr: %s", cfg.HTTPAddr)
	}
	if cfg.RabbitMQAddr != defaultRabbitMQAddr {
		t.Fatalf("unexpected default RabbitMQAddr: %s", cfg.RabbitMQAddr)
	}
	if cfg.RabbitMQExchange != defaultRabbitMQExchange {
		t.Fatalf("unexpected default RabbitMQExchange: %s", cfg.RabbitMQExchange)
	}
	if len(cfg.RabbitMQExchanges) != 2 || cfg.RabbitMQExchanges[0] != "shiori.order.event" || cfg.RabbitMQExchanges[1] != "shiori.user.event" {
		t.Fatalf("unexpected default RabbitMQExchanges: %+v", cfg.RabbitMQExchanges)
	}
	if cfg.RabbitMQQueue != defaultRabbitMQQueue {
		t.Fatalf("unexpected default RabbitMQQueue: %s", cfg.RabbitMQQueue)
	}
	if cfg.LogLevel != zerolog.InfoLevel {
		t.Fatalf("unexpected default LogLevel: %v", cfg.LogLevel)
	}
	if cfg.WSWriteTimeout != defaultWSWriteTimeout {
		t.Fatalf("unexpected default WSWriteTimeout: %s", cfg.WSWriteTimeout)
	}
	if cfg.WSPingInterval != defaultWSPingInterval {
		t.Fatalf("unexpected default WSPingInterval: %s", cfg.WSPingInterval)
	}
	if cfg.MetricsEnabled != defaultMetricsEnabled {
		t.Fatalf("unexpected default MetricsEnabled: %t", cfg.MetricsEnabled)
	}
	if cfg.StoreDriver != defaultStoreDriver {
		t.Fatalf("unexpected default StoreDriver: %s", cfg.StoreDriver)
	}
	if cfg.StoreMaxPerUser != defaultStoreMaxPerUser {
		t.Fatalf("unexpected default StoreMaxPerUser: %d", cfg.StoreMaxPerUser)
	}
	if cfg.ReplayDefaultLimit != defaultReplayLimit {
		t.Fatalf("unexpected default ReplayDefaultLimit: %d", cfg.ReplayDefaultLimit)
	}
	if cfg.ReplayMaxLimit != defaultReplayMaxLimit {
		t.Fatalf("unexpected default ReplayMaxLimit: %d", cfg.ReplayMaxLimit)
	}
	if cfg.WSReplayLimit != defaultWSReplayLimit {
		t.Fatalf("unexpected default WSReplayLimit: %d", cfg.WSReplayLimit)
	}
	if cfg.AuthEnabled != defaultAuthEnabled {
		t.Fatalf("unexpected default AuthEnabled: %t", cfg.AuthEnabled)
	}
	if cfg.JWTIssuer != defaultJWTIssuer {
		t.Fatalf("unexpected default JWTIssuer: %s", cfg.JWTIssuer)
	}
	if cfg.MySQLMaxOpenConns != defaultMySQLMaxOpenConns {
		t.Fatalf("unexpected default MySQLMaxOpenConns: %d", cfg.MySQLMaxOpenConns)
	}
	if cfg.MySQLMaxIdleConns != defaultMySQLMaxIdleConns {
		t.Fatalf("unexpected default MySQLMaxIdleConns: %d", cfg.MySQLMaxIdleConns)
	}
	if cfg.MySQLConnMaxLifetime != defaultMySQLConnMaxLifetime {
		t.Fatalf("unexpected default MySQLConnMaxLifetime: %s", cfg.MySQLConnMaxLifetime)
	}
}

func TestLoadOverrideAndFallback(t *testing.T) {
	t.Setenv("NOTIFY_HTTP_ADDR", ":18090")
	t.Setenv("RABBITMQ_ADDR", "amqp://guest:guest@127.0.0.1:5672/")
	t.Setenv("RABBITMQ_EXCHANGES", "shiori.order.event,shiori.user.event")
	t.Setenv("RABBITMQ_QUEUE", "notify.event")
	t.Setenv("RABBITMQ_ROUTING_KEYS", "order.paid,user.status.changed")
	t.Setenv("LOG_LEVEL", "debug")
	t.Setenv("WS_WRITE_TIMEOUT", "not-a-duration")
	t.Setenv("WS_PING_INTERVAL", "45s")
	t.Setenv("NOTIFY_METRICS_ENABLED", "false")
	t.Setenv("NOTIFY_STORE_DRIVER", "MySQL")
	t.Setenv("NOTIFY_EVENT_STORE_MAX_PER_USER", "500")
	t.Setenv("NOTIFY_REPLAY_DEFAULT_LIMIT", "20")
	t.Setenv("NOTIFY_REPLAY_MAX_LIMIT", "120")
	t.Setenv("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", "80")
	t.Setenv("NOTIFY_AUTH_ENABLED", "false")
	t.Setenv("NOTIFY_JWT_HMAC_SECRET", "secret")
	t.Setenv("NOTIFY_JWT_ISSUER", "test-issuer")
	t.Setenv("NOTIFY_MYSQL_DSN", "user:pwd@tcp(127.0.0.1:3306)/shiori_notify")
	t.Setenv("NOTIFY_MYSQL_MAX_OPEN_CONNS", "30")
	t.Setenv("NOTIFY_MYSQL_MAX_IDLE_CONNS", "15")
	t.Setenv("NOTIFY_MYSQL_CONN_MAX_LIFETIME", "1h")

	cfg := Load()

	if cfg.HTTPAddr != ":18090" {
		t.Fatalf("unexpected HTTPAddr: %s", cfg.HTTPAddr)
	}
	if cfg.RabbitMQAddr != "amqp://guest:guest@127.0.0.1:5672/" {
		t.Fatalf("unexpected RabbitMQAddr: %s", cfg.RabbitMQAddr)
	}
	if len(cfg.RabbitMQExchanges) != 2 || cfg.RabbitMQExchanges[1] != "shiori.user.event" {
		t.Fatalf("unexpected RabbitMQExchanges: %+v", cfg.RabbitMQExchanges)
	}
	if len(cfg.RabbitMQRoutingKeys) != 2 || cfg.RabbitMQRoutingKeys[1] != "user.status.changed" {
		t.Fatalf("unexpected RabbitMQRoutingKeys: %+v", cfg.RabbitMQRoutingKeys)
	}
	if cfg.RabbitMQExchange != "shiori.order.event" {
		t.Fatalf("unexpected RabbitMQExchange: %s", cfg.RabbitMQExchange)
	}
	if cfg.RabbitMQRoutingKey != "order.paid" {
		t.Fatalf("unexpected RabbitMQRoutingKey: %s", cfg.RabbitMQRoutingKey)
	}
	if cfg.RabbitMQQueue != "notify.event" {
		t.Fatalf("unexpected RabbitMQQueue: %s", cfg.RabbitMQQueue)
	}
	if cfg.LogLevel != zerolog.DebugLevel {
		t.Fatalf("unexpected LogLevel: %v", cfg.LogLevel)
	}
	if cfg.WSWriteTimeout != defaultWSWriteTimeout {
		t.Fatalf("invalid duration should fallback, got: %s", cfg.WSWriteTimeout)
	}
	if cfg.WSPingInterval != 45*time.Second {
		t.Fatalf("unexpected WSPingInterval: %s", cfg.WSPingInterval)
	}
	if cfg.MetricsEnabled {
		t.Fatalf("unexpected MetricsEnabled: %t", cfg.MetricsEnabled)
	}
	if cfg.StoreDriver != "mysql" {
		t.Fatalf("unexpected StoreDriver: %s", cfg.StoreDriver)
	}
	if cfg.StoreMaxPerUser != 500 {
		t.Fatalf("unexpected StoreMaxPerUser: %d", cfg.StoreMaxPerUser)
	}
	if cfg.ReplayDefaultLimit != 20 {
		t.Fatalf("unexpected ReplayDefaultLimit: %d", cfg.ReplayDefaultLimit)
	}
	if cfg.ReplayMaxLimit != 120 {
		t.Fatalf("unexpected ReplayMaxLimit: %d", cfg.ReplayMaxLimit)
	}
	if cfg.WSReplayLimit != 80 {
		t.Fatalf("unexpected WSReplayLimit: %d", cfg.WSReplayLimit)
	}
	if cfg.AuthEnabled {
		t.Fatalf("unexpected AuthEnabled: %t", cfg.AuthEnabled)
	}
	if cfg.JWTHmacSecret != "secret" {
		t.Fatalf("unexpected JWTHmacSecret: %s", cfg.JWTHmacSecret)
	}
	if cfg.JWTIssuer != "test-issuer" {
		t.Fatalf("unexpected JWTIssuer: %s", cfg.JWTIssuer)
	}
	if cfg.MySQLDSN != "user:pwd@tcp(127.0.0.1:3306)/shiori_notify" {
		t.Fatalf("unexpected MySQLDSN: %s", cfg.MySQLDSN)
	}
	if cfg.MySQLMaxOpenConns != 30 {
		t.Fatalf("unexpected MySQLMaxOpenConns: %d", cfg.MySQLMaxOpenConns)
	}
	if cfg.MySQLMaxIdleConns != 15 {
		t.Fatalf("unexpected MySQLMaxIdleConns: %d", cfg.MySQLMaxIdleConns)
	}
	if cfg.MySQLConnMaxLifetime != time.Hour {
		t.Fatalf("unexpected MySQLConnMaxLifetime: %s", cfg.MySQLConnMaxLifetime)
	}
}

func TestParseCSV(t *testing.T) {
	items := parseCSV("a, b, a, ,c")
	if len(items) != 3 {
		t.Fatalf("unexpected item len: %d", len(items))
	}
	if items[0] != "a" || items[1] != "b" || items[2] != "c" {
		t.Fatalf("unexpected parse result: %+v", items)
	}
}
