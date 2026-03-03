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
}

func TestLoadMetricsInvalidFallback(t *testing.T) {
	t.Setenv("NOTIFY_METRICS_ENABLED", "invalid")
	cfg := Load()
	if cfg.MetricsEnabled != defaultMetricsEnabled {
		t.Fatalf("invalid bool should fallback, got: %t", cfg.MetricsEnabled)
	}
}
