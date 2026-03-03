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
	t.Setenv("LOG_LEVEL", "")
	t.Setenv("WS_WRITE_TIMEOUT", "")
	t.Setenv("WS_PING_INTERVAL", "")
	t.Setenv("NOTIFY_METRICS_ENABLED", "")
	t.Setenv("NOTIFY_EVENT_STORE_MAX_PER_USER", "")
	t.Setenv("NOTIFY_REPLAY_DEFAULT_LIMIT", "")
	t.Setenv("NOTIFY_REPLAY_MAX_LIMIT", "")
	t.Setenv("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", "")

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
	if cfg.RabbitMQQueue != defaultRabbitMQQueue {
		t.Fatalf("unexpected default RabbitMQQueue: %s", cfg.RabbitMQQueue)
	}
	if cfg.RabbitMQRoutingKey != defaultRabbitMQRouteKey {
		t.Fatalf("unexpected default RabbitMQRoutingKey: %s", cfg.RabbitMQRoutingKey)
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
}

func TestLoadOverrideAndFallback(t *testing.T) {
	t.Setenv("NOTIFY_HTTP_ADDR", ":18090")
	t.Setenv("RABBITMQ_ADDR", "amqp://guest:guest@127.0.0.1:5672/")
	t.Setenv("RABBITMQ_EXCHANGE", "custom.exchange")
	t.Setenv("RABBITMQ_QUEUE", "custom.queue")
	t.Setenv("RABBITMQ_ROUTING_KEY", "custom.key")
	t.Setenv("LOG_LEVEL", "debug")
	t.Setenv("WS_WRITE_TIMEOUT", "not-a-duration")
	t.Setenv("WS_PING_INTERVAL", "45s")
	t.Setenv("NOTIFY_METRICS_ENABLED", "false")
	t.Setenv("NOTIFY_EVENT_STORE_MAX_PER_USER", "500")
	t.Setenv("NOTIFY_REPLAY_DEFAULT_LIMIT", "20")
	t.Setenv("NOTIFY_REPLAY_MAX_LIMIT", "120")
	t.Setenv("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", "80")

	cfg := Load()

	if cfg.HTTPAddr != ":18090" {
		t.Fatalf("unexpected HTTPAddr: %s", cfg.HTTPAddr)
	}
	if cfg.RabbitMQAddr != "amqp://guest:guest@127.0.0.1:5672/" {
		t.Fatalf("unexpected RabbitMQAddr: %s", cfg.RabbitMQAddr)
	}
	if cfg.RabbitMQExchange != "custom.exchange" {
		t.Fatalf("unexpected RabbitMQExchange: %s", cfg.RabbitMQExchange)
	}
	if cfg.RabbitMQQueue != "custom.queue" {
		t.Fatalf("unexpected RabbitMQQueue: %s", cfg.RabbitMQQueue)
	}
	if cfg.RabbitMQRoutingKey != "custom.key" {
		t.Fatalf("unexpected RabbitMQRoutingKey: %s", cfg.RabbitMQRoutingKey)
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
}

func TestLoadMetricsInvalidFallback(t *testing.T) {
	t.Setenv("NOTIFY_METRICS_ENABLED", "invalid")
	t.Setenv("NOTIFY_EVENT_STORE_MAX_PER_USER", "invalid")
	t.Setenv("NOTIFY_REPLAY_DEFAULT_LIMIT", "-1")
	t.Setenv("NOTIFY_REPLAY_MAX_LIMIT", "0")
	t.Setenv("NOTIFY_WS_REPLAY_DEFAULT_LIMIT", "invalid")
	cfg := Load()
	if cfg.MetricsEnabled != defaultMetricsEnabled {
		t.Fatalf("invalid bool should fallback, got: %t", cfg.MetricsEnabled)
	}
	if cfg.StoreMaxPerUser != defaultStoreMaxPerUser {
		t.Fatalf("invalid int should fallback, got: %d", cfg.StoreMaxPerUser)
	}
	if cfg.ReplayDefaultLimit != defaultReplayLimit {
		t.Fatalf("invalid int should fallback, got: %d", cfg.ReplayDefaultLimit)
	}
	if cfg.ReplayMaxLimit != defaultReplayMaxLimit {
		t.Fatalf("invalid int should fallback, got: %d", cfg.ReplayMaxLimit)
	}
	if cfg.WSReplayLimit != defaultWSReplayLimit {
		t.Fatalf("invalid int should fallback, got: %d", cfg.WSReplayLimit)
	}
}
