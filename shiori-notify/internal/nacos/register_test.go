package nacos

import "testing"

func TestParseNacosAddress(t *testing.T) {
	host, port, err := parseNacosAddress("nacos:8848")
	if err != nil {
		t.Fatalf("parseNacosAddress failed: %v", err)
	}
	if host != "nacos" || port != 8848 {
		t.Fatalf("unexpected address parse result: %s:%d", host, port)
	}
}

func TestParseNacosAddressWithScheme(t *testing.T) {
	host, port, err := parseNacosAddress("http://127.0.0.1:18848")
	if err != nil {
		t.Fatalf("parseNacosAddress failed: %v", err)
	}
	if host != "127.0.0.1" || port != 18848 {
		t.Fatalf("unexpected address parse result: %s:%d", host, port)
	}
}

func TestParseListenPort(t *testing.T) {
	port, err := parseListenPort(":8090")
	if err != nil {
		t.Fatalf("parseListenPort failed: %v", err)
	}
	if port != 8090 {
		t.Fatalf("unexpected listen port: %d", port)
	}
}

func TestMergeMaps(t *testing.T) {
	base := map[string]any{
		"notify": map[string]any{
			"chat": map[string]any{
				"enabled": false,
			},
		},
	}
	override := map[string]any{
		"notify": map[string]any{
			"chat": map[string]any{
				"enabled": true,
			},
		},
	}
	merged := mergeMaps(base, override)
	notifyMap := merged["notify"].(map[string]any)
	chatMap := notifyMap["chat"].(map[string]any)
	enabled := chatMap["enabled"].(bool)
	if !enabled {
		t.Fatalf("expected override value to win")
	}
}
