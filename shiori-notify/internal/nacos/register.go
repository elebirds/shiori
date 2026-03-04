package nacos

import (
	"context"
	"fmt"
	"net"
	"strconv"
	"strings"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	naming_client "github.com/nacos-group/nacos-sdk-go/v2/clients/naming_client"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
)

const notifyServiceName = "shiori-notify-service"

func RegisterNotifyInstance(ctx context.Context, conn config.NacosConnConfig, cfg config.Config) (func() error, error) {
	namingClient, err := newNamingClient(conn)
	if err != nil {
		return nil, fmt.Errorf("create nacos naming client failed: %w", err)
	}
	instanceIP, err := pickInstanceIP()
	if err != nil {
		return nil, fmt.Errorf("resolve notify instance ip failed: %w", err)
	}
	instancePort, err := parseListenPort(cfg.HTTPAddr)
	if err != nil {
		return nil, err
	}

	registerParam := vo.RegisterInstanceParam{
		Ip:          instanceIP,
		Port:        uint64(instancePort),
		ServiceName: notifyServiceName,
		Healthy:     true,
		Enable:      true,
		Ephemeral:   true,
		Weight:      10,
		Metadata: map[string]string{
			"instanceId": cfg.InstanceID,
		},
	}
	registered, err := namingClient.RegisterInstance(registerParam)
	if err != nil {
		return nil, fmt.Errorf("register nacos instance failed: %w", err)
	}
	if !registered {
		return nil, fmt.Errorf("register nacos instance failed without error")
	}

	cleanup := func() error {
		deregistered, deregErr := namingClient.DeregisterInstance(vo.DeregisterInstanceParam{
			Ip:          instanceIP,
			Port:        uint64(instancePort),
			ServiceName: notifyServiceName,
			Ephemeral:   true,
		})
		if deregErr != nil {
			return fmt.Errorf("deregister nacos instance failed: %w", deregErr)
		}
		if !deregistered {
			return fmt.Errorf("deregister nacos instance failed without error")
		}
		return nil
	}

	go func(client naming_client.INamingClient, ip string, port int) {
		<-ctx.Done()
		_, _ = client.DeregisterInstance(vo.DeregisterInstanceParam{
			Ip:          ip,
			Port:        uint64(port),
			ServiceName: notifyServiceName,
			Ephemeral:   true,
		})
	}(namingClient, instanceIP, instancePort)

	return cleanup, nil
}

func parseNacosAddress(raw string) (string, uint64, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return "", 0, fmt.Errorf("NACOS_ADDR is empty")
	}
	if strings.HasPrefix(trimmed, "http://") {
		trimmed = strings.TrimPrefix(trimmed, "http://")
	}
	if strings.HasPrefix(trimmed, "https://") {
		trimmed = strings.TrimPrefix(trimmed, "https://")
	}
	host, portText, err := net.SplitHostPort(trimmed)
	if err != nil {
		host = trimmed
		portText = "8848"
	}
	host = strings.TrimSpace(host)
	if host == "" {
		return "", 0, fmt.Errorf("invalid NACOS_ADDR: %s", raw)
	}
	port, err := strconv.ParseUint(strings.TrimSpace(portText), 10, 64)
	if err != nil || port == 0 {
		return "", 0, fmt.Errorf("invalid nacos port in NACOS_ADDR: %s", raw)
	}
	return host, port, nil
}

func parseListenPort(httpAddr string) (int, error) {
	trimmed := strings.TrimSpace(httpAddr)
	if trimmed == "" {
		return 0, fmt.Errorf("notify.http.addr is empty")
	}
	if strings.HasPrefix(trimmed, ":") {
		trimmed = "0.0.0.0" + trimmed
	}
	addr, err := net.ResolveTCPAddr("tcp", trimmed)
	if err != nil {
		return 0, fmt.Errorf("parse notify.http.addr failed: %w", err)
	}
	if addr.Port <= 0 {
		return 0, fmt.Errorf("notify.http.addr has invalid port: %s", httpAddr)
	}
	return addr.Port, nil
}

func pickInstanceIP() (string, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", err
	}
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, addrErr := iface.Addrs()
		if addrErr != nil {
			continue
		}
		for _, addr := range addrs {
			ip := extractIP(addr)
			if ip == nil || ip.IsLoopback() {
				continue
			}
			ipv4 := ip.To4()
			if ipv4 == nil {
				continue
			}
			return ipv4.String(), nil
		}
	}
	return "", fmt.Errorf("no non-loopback ipv4 address found")
}

func extractIP(addr net.Addr) net.IP {
	switch value := addr.(type) {
	case *net.IPNet:
		return value.IP
	case *net.IPAddr:
		return value.IP
	default:
		return nil
	}
}
