package nacos

import (
	"fmt"
	"strings"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	config_client "github.com/nacos-group/nacos-sdk-go/v2/clients/config_client"
	naming_client "github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"gopkg.in/yaml.v3"
)

const (
	notifyDataIDBase     = "shiori-notify-service-base.yml"
	notifyDataIDSecret   = "shiori-notify-service-secret.yml"
	securityDataIDBase   = "shiori-security-base.yml"
	securityDataIDSecret = "shiori-security-secret.yml"
)

var notifyDataIDs = []string{
	notifyDataIDBase,
	notifyDataIDSecret,
	securityDataIDBase,
	securityDataIDSecret,
}

func LoadNotifyConfigFromNacos(conn config.NacosConnConfig) (config.NotifyNacosConfig, error) {
	configClient, err := newConfigClient(conn)
	if err != nil {
		return config.NotifyNacosConfig{}, err
	}

	merged := map[string]any{}
	for i := range notifyDataIDs {
		dataID := notifyDataIDs[i]
		content, getErr := configClient.GetConfig(vo.ConfigParam{
			DataId: dataID,
			Group:  conn.Group,
		})
		if getErr != nil {
			return config.NotifyNacosConfig{}, fmt.Errorf("load nacos config failed, dataId=%s group=%s: %w", dataID, conn.Group, getErr)
		}
		if strings.TrimSpace(content) == "" {
			return config.NotifyNacosConfig{}, fmt.Errorf("nacos config is empty, dataId=%s group=%s", dataID, conn.Group)
		}

		doc := map[string]any{}
		if unmarshalErr := yaml.Unmarshal([]byte(content), &doc); unmarshalErr != nil {
			return config.NotifyNacosConfig{}, fmt.Errorf("parse nacos yaml failed, dataId=%s: %w", dataID, unmarshalErr)
		}
		merged = mergeMaps(merged, normalizeValue(doc).(map[string]any))
	}

	combinedBytes, marshalErr := yaml.Marshal(merged)
	if marshalErr != nil {
		return config.NotifyNacosConfig{}, fmt.Errorf("marshal merged nacos config failed: %w", marshalErr)
	}

	var nacosCfg config.NotifyNacosConfig
	if unmarshalErr := yaml.Unmarshal(combinedBytes, &nacosCfg); unmarshalErr != nil {
		return config.NotifyNacosConfig{}, fmt.Errorf("decode merged notify config failed: %w", unmarshalErr)
	}
	return nacosCfg, nil
}

func LoadRuntimeConfigFromNacos(conn config.NacosConnConfig) (config.Config, error) {
	nacosCfg, err := LoadNotifyConfigFromNacos(conn)
	if err != nil {
		return config.Config{}, err
	}
	runtimeCfg, err := nacosCfg.ToRuntimeConfig()
	if err != nil {
		return config.Config{}, err
	}
	return runtimeCfg, nil
}

func newConfigClient(conn config.NacosConnConfig) (config_client.IConfigClient, error) {
	serverConfig, clientConfig, err := buildClientParams(conn)
	if err != nil {
		return nil, err
	}
	return clients.NewConfigClient(vo.NacosClientParam{
		ClientConfig:  clientConfig,
		ServerConfigs: serverConfig,
	})
}

func newNamingClient(conn config.NacosConnConfig) (naming_client.INamingClient, error) {
	serverConfig, clientConfig, err := buildClientParams(conn)
	if err != nil {
		return nil, err
	}
	return clients.NewNamingClient(vo.NacosClientParam{
		ClientConfig:  clientConfig,
		ServerConfigs: serverConfig,
	})
}

func buildClientParams(conn config.NacosConnConfig) ([]constant.ServerConfig, *constant.ClientConfig, error) {
	host, port, err := parseNacosAddress(conn.Addr)
	if err != nil {
		return nil, nil, err
	}
	serverConfigs := []constant.ServerConfig{{
		IpAddr: host,
		Port:   port,
	}}
	clientConfig := &constant.ClientConfig{
		NamespaceId:         conn.Namespace,
		TimeoutMs:           5000,
		NotLoadCacheAtStart: true,
		LogDir:              "/tmp/nacos/log",
		CacheDir:            "/tmp/nacos/cache",
		LogLevel:            "warn",
		Username:            conn.Username,
		Password:            conn.Password,
	}
	return serverConfigs, clientConfig, nil
}

func mergeMaps(base, override map[string]any) map[string]any {
	if base == nil {
		base = map[string]any{}
	}
	for key, overrideValue := range override {
		if baseMap, ok := base[key].(map[string]any); ok {
			if overrideMap, ok2 := overrideValue.(map[string]any); ok2 {
				base[key] = mergeMaps(baseMap, overrideMap)
				continue
			}
		}
		base[key] = overrideValue
	}
	return base
}

func normalizeValue(raw any) any {
	switch value := raw.(type) {
	case map[string]any:
		normalized := make(map[string]any, len(value))
		for key, item := range value {
			normalized[key] = normalizeValue(item)
		}
		return normalized
	case map[any]any:
		normalized := make(map[string]any, len(value))
		for key, item := range value {
			normalized[fmt.Sprint(key)] = normalizeValue(item)
		}
		return normalized
	case []any:
		normalized := make([]any, 0, len(value))
		for i := range value {
			normalized = append(normalized, normalizeValue(value[i]))
		}
		return normalized
	default:
		return raw
	}
}
