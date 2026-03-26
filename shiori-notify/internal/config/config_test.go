package config

import (
	"os"
	"testing"
)

func TestLoadNacosConnFromEnv(t *testing.T) {
	t.Setenv("NACOS_ADDR", "127.0.0.1:8848")
	t.Setenv("NACOS_USERNAME", "nacos")
	t.Setenv("NACOS_PASSWORD", "secret")
	t.Setenv("NACOS_CONFIG_GROUP", "SHIORI_TEST")
	t.Setenv("NACOS_CONFIG_NAMESPACE", "abc")

	cfg, err := LoadNacosConnFromEnv()
	if err != nil {
		t.Fatalf("LoadNacosConnFromEnv failed: %v", err)
	}
	if cfg.Addr != "127.0.0.1:8848" || cfg.Username != "nacos" || cfg.Password != "secret" {
		t.Fatalf("unexpected nacos conn config: %+v", cfg)
	}
	if cfg.Group != "SHIORI_TEST" || cfg.Namespace != "abc" {
		t.Fatalf("unexpected group/namespace: %+v", cfg)
	}
}

func TestLoadNacosConnFromEnvRequiresCredentials(t *testing.T) {
	_ = os.Unsetenv("NACOS_USERNAME")
	_ = os.Unsetenv("NACOS_PASSWORD")
	t.Setenv("NACOS_ADDR", "127.0.0.1:8848")

	if _, err := LoadNacosConnFromEnv(); err == nil {
		t.Fatalf("expected error when nacos credentials are missing")
	}
}

func TestNotifyNacosConfigToRuntimeConfig(t *testing.T) {
	metricsEnabled := true
	authEnabled := true
	chatEnabled := true
	chatPubSubEnabled := true

	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Redis.Addr = "redis:6379"
	nacosCfg.Notify.Log.Level = "info"
	nacosCfg.Notify.WS.Path = "/ws"
	nacosCfg.Notify.WS.WriteTimeout = "5s"
	nacosCfg.Notify.WS.PingInterval = "30s"
	nacosCfg.Notify.Metrics.Enabled = &metricsEnabled
	nacosCfg.Notify.Store.Driver = "mysql"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Notify.MySQL.DSN = "notify_service:pwd@tcp(mysql:3306)/shiori_notify?charset=utf8mb4&parseTime=true&loc=UTC"
	nacosCfg.Notify.MySQL.MaxOpenConns = 20
	nacosCfg.Notify.MySQL.MaxIdleConns = 10
	nacosCfg.Notify.MySQL.ConnMaxLifetime = "30m"
	nacosCfg.Notify.Chat.Enabled = &chatEnabled
	nacosCfg.Notify.Chat.DefaultLimit = 20
	nacosCfg.Notify.Chat.MaxLimit = 100
	nacosCfg.Notify.Chat.TicketIssuer = "shiori-chat-ticket"
	nacosCfg.Notify.Chat.TicketPublicKey = "pub"
	nacosCfg.Notify.Chat.PubSubEnabled = &chatPubSubEnabled
	nacosCfg.Notify.Chat.PubSubChannel = "shiori.chat.event"
	nacosCfg.Notify.Instance.ID = "notify-1"
	nacosCfg.Security.JWT.Issuer = "shiori"
	nacosCfg.Security.JWT.HMACSecret = "hmac"

	cfg, err := nacosCfg.ToRuntimeConfig()
	if err != nil {
		t.Fatalf("ToRuntimeConfig failed: %v", err)
	}
	if cfg.StoreDriver != "mysql" || !cfg.ChatEnabled || cfg.JWTHmacSecret != "hmac" {
		t.Fatalf("unexpected runtime config: %+v", cfg)
	}
}

func TestNotifyNacosConfigToRuntimeConfigValidate(t *testing.T) {
	authEnabled := true
	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Store.Driver = "mysql"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Security.JWT.Issuer = "shiori"
	// intentionally missing jwt secret and mysql dsn

	if _, err := nacosCfg.ToRuntimeConfig(); err == nil {
		t.Fatalf("expected validation error when required fields are missing")
	}
}

func TestNotifyNacosConfigToRuntimeConfigWithKafkaEventBus(t *testing.T) {
	kafkaEnabled := true
	authEnabled := false

	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Store.Driver = "memory"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Notify.Kafka.Enabled = &kafkaEnabled
	nacosCfg.Notify.Kafka.Brokers = "127.0.0.1:9092"
	nacosCfg.Notify.Kafka.Topics = "shiori.cdc.order.outbox.raw,shiori.cdc.user.outbox.raw"
	nacosCfg.Notify.Kafka.GroupID = "shiori-notify-cdc"

	cfg, err := nacosCfg.ToRuntimeConfig()
	if err != nil {
		t.Fatalf("ToRuntimeConfig failed: %v", err)
	}
	if !cfg.KafkaEnabled {
		t.Fatalf("expected kafka enabled, got %+v", cfg)
	}
	if len(cfg.KafkaTopics) != 2 {
		t.Fatalf("expected 2 kafka topics, got %+v", cfg.KafkaTopics)
	}
}

func TestNotifyNacosConfigToRuntimeConfigDefaultsToKafkaEventBus(t *testing.T) {
	authEnabled := false
	chatEnabled := true
	chatPubSubEnabled := true

	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Store.Driver = "mysql"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Notify.Chat.Enabled = &chatEnabled
	nacosCfg.Notify.Chat.PubSubEnabled = &chatPubSubEnabled
	nacosCfg.Notify.Chat.PubSubChannel = "shiori.chat.event"
	nacosCfg.Notify.Chat.TicketIssuer = "shiori-chat-ticket"
	nacosCfg.Notify.Chat.TicketPublicKey = "pub"
	nacosCfg.Notify.MySQL.DSN = "notify_service:pwd@tcp(mysql:3306)/shiori_notify?charset=utf8mb4&parseTime=true&loc=UTC"
	nacosCfg.Notify.Redis.Addr = "redis:6379"

	cfg, err := nacosCfg.ToRuntimeConfig()
	if err != nil {
		t.Fatalf("expected kafka event bus defaults, got error: %v", err)
	}
	if !cfg.KafkaEnabled {
		t.Fatalf("expected kafka enabled by default, got %+v", cfg)
	}
}

func TestNotifyNacosConfigToRuntimeConfigRejectsKafkaDisabled(t *testing.T) {
	kafkaEnabled := false
	authEnabled := false

	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Store.Driver = "memory"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Notify.Kafka.Enabled = &kafkaEnabled

	if _, err := nacosCfg.ToRuntimeConfig(); err == nil {
		t.Fatalf("expected error when kafka event bus is disabled")
	}
}
