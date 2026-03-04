package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

type envelope struct {
	EventID     string          `json:"eventId"`
	Type        string          `json:"type"`
	AggregateID string          `json:"aggregateId"`
	CreatedAt   string          `json:"createdAt"`
	Payload     json.RawMessage `json:"payload"`
}

func main() {
	var (
		wsURL           string
		baseURL         string
		accessToken     string
		lastEventID     string
		expectType      string
		expectAggregate string
		timeoutText     string
	)

	flag.StringVar(&wsURL, "url", "", "完整 WebSocket URL（可选）")
	flag.StringVar(&baseURL, "base-url", "ws://localhost:8090/ws", "WebSocket 基础地址")
	flag.StringVar(&accessToken, "access-token", "", "访问令牌（必填）")
	flag.StringVar(&lastEventID, "last-event-id", "", "补偿游标（可选）")
	flag.StringVar(&expectType, "expect-type", "OrderPaid", "期望事件类型")
	flag.StringVar(&expectAggregate, "expect-aggregate", "", "期望 aggregateId（可选）")
	flag.StringVar(&timeoutText, "timeout", "60s", "等待超时时间")
	flag.Parse()

	if strings.TrimSpace(accessToken) == "" {
		exitf("access-token 不能为空")
	}

	timeout, err := time.ParseDuration(timeoutText)
	if err != nil || timeout <= 0 {
		exitf("timeout 无效: %s", timeoutText)
	}

	targetURL, err := resolveTargetURL(wsURL, baseURL, accessToken, lastEventID)
	if err != nil {
		exitf("构建 ws URL 失败: %v", err)
	}

	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, _, err := dialer.Dial(targetURL, nil)
	if err != nil {
		exitf("连接 WebSocket 失败: %v", err)
	}
	defer conn.Close()

	deadline := time.Now().Add(timeout)
	for {
		if err := conn.SetReadDeadline(deadline); err != nil {
			exitf("设置读取超时失败: %v", err)
		}

		_, message, err := conn.ReadMessage()
		if err != nil {
			if isTimeoutError(err) {
				exitf("等待超时，未收到匹配事件（type=%s, aggregateId=%s）", expectType, expectAggregate)
			}
			exitf("读取消息失败: %v", err)
		}

		var env envelope
		if err := json.Unmarshal(message, &env); err != nil {
			continue
		}
		if expectType != "" && env.Type != expectType {
			continue
		}
		if expectAggregate != "" && env.AggregateID != expectAggregate {
			continue
		}

		fmt.Printf("matched event: eventId=%s type=%s aggregateId=%s\n", env.EventID, env.Type, env.AggregateID)
		return
	}
}

func resolveTargetURL(rawURL, baseURL, accessToken, lastEventID string) (string, error) {
	if strings.TrimSpace(rawURL) == "" {
		rawURL = baseURL
	}
	u, err := url.Parse(rawURL)
	if err != nil {
		return "", err
	}
	q := u.Query()
	q.Set("accessToken", accessToken)
	if strings.TrimSpace(lastEventID) != "" {
		q.Set("lastEventId", strings.TrimSpace(lastEventID))
	}
	u.RawQuery = q.Encode()
	return u.String(), nil
}

func isTimeoutError(err error) bool {
	if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
		return true
	}
	return strings.Contains(strings.ToLower(err.Error()), "timeout")
}

func exitf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "[ws-smoke] "+format+"\n", args...)
	os.Exit(1)
}
