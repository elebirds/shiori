package config

import "testing"

func TestNotifyNacosConfigToRuntimeConfigUsesRedisPubSubForChat(t *testing.T) {
	metricsEnabled := true
	authEnabled := true
	chatEnabled := true
	pubSubEnabled := true

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
	nacosCfg.Notify.Chat.PubSubEnabled = &pubSubEnabled
	nacosCfg.Notify.Chat.PubSubChannel = "shiori.chat.event"
	nacosCfg.Notify.Instance.ID = "notify-1"
	nacosCfg.Security.JWT.Issuer = "shiori"
	nacosCfg.Security.JWT.HMACSecret = "hmac"

	cfg, err := nacosCfg.ToRuntimeConfig()
	if err != nil {
		t.Fatalf("ToRuntimeConfig failed: %v", err)
	}
	if cfg.RedisAddr != "redis:6379" || !cfg.ChatPubSubEnabled || cfg.ChatPubSubChannel != "shiori.chat.event" {
		t.Fatalf("unexpected redis pubsub config: %+v", cfg)
	}
}

func TestNotifyNacosConfigToRuntimeConfigRequiresRedisWhenChatPubSubEnabled(t *testing.T) {
	authEnabled := false
	chatEnabled := true
	pubSubEnabled := true

	nacosCfg := NotifyNacosConfig{}
	nacosCfg.Notify.HTTP.Addr = ":8090"
	nacosCfg.Notify.Store.Driver = "mysql"
	nacosCfg.Notify.Store.MaxPerUser = 1000
	nacosCfg.Notify.Replay.DefaultLimit = 50
	nacosCfg.Notify.Replay.MaxLimit = 200
	nacosCfg.Notify.Replay.WSLimit = 100
	nacosCfg.Notify.Auth.Enabled = &authEnabled
	nacosCfg.Notify.Chat.Enabled = &chatEnabled
	nacosCfg.Notify.Chat.PubSubEnabled = &pubSubEnabled
	nacosCfg.Notify.Chat.PubSubChannel = "shiori.chat.event"
	nacosCfg.Notify.Chat.TicketIssuer = "shiori-chat-ticket"
	nacosCfg.Notify.Chat.TicketPublicKey = "pub"
	nacosCfg.Notify.MySQL.DSN = "notify_service:pwd@tcp(mysql:3306)/shiori_notify?charset=utf8mb4&parseTime=true&loc=UTC"

	if _, err := nacosCfg.ToRuntimeConfig(); err == nil {
		t.Fatalf("expected error when redis address is missing")
	}
}
