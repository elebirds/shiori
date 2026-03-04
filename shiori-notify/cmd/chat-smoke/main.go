package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

type wsFrame map[string]any

func main() {
	var (
		baseURL           string
		buyerAccessToken  string
		sellerAccessToken string
		chatTicket        string
		conversationID    int64
		clientMsgID       string
		content           string
		timeoutText       string
	)

	flag.StringVar(&baseURL, "base-url", "ws://localhost:8090/ws", "WebSocket 基础地址")
	flag.StringVar(&buyerAccessToken, "buyer-access-token", "", "买家 access token（必填）")
	flag.StringVar(&sellerAccessToken, "seller-access-token", "", "卖家 access token（必填）")
	flag.StringVar(&chatTicket, "chat-ticket", "", "chat ticket（必填）")
	flag.Int64Var(&conversationID, "conversation-id", 0, "预期会话 ID（可选）")
	flag.StringVar(&clientMsgID, "client-msg-id", "", "客户端消息 ID（必填）")
	flag.StringVar(&content, "content", "", "发送消息内容（必填）")
	flag.StringVar(&timeoutText, "timeout", "30s", "整体超时时间")
	flag.Parse()

	if strings.TrimSpace(buyerAccessToken) == "" {
		exitf("buyer-access-token 不能为空")
	}
	if strings.TrimSpace(sellerAccessToken) == "" {
		exitf("seller-access-token 不能为空")
	}
	if strings.TrimSpace(chatTicket) == "" {
		exitf("chat-ticket 不能为空")
	}
	if strings.TrimSpace(clientMsgID) == "" {
		exitf("client-msg-id 不能为空")
	}
	if strings.TrimSpace(content) == "" {
		exitf("content 不能为空")
	}

	timeout, err := time.ParseDuration(timeoutText)
	if err != nil || timeout <= 0 {
		exitf("timeout 无效: %s", timeoutText)
	}
	deadline := time.Now().Add(timeout)

	buyerConn, err := dial(baseURL, buyerAccessToken)
	if err != nil {
		exitf("buyer 连接失败: %v", err)
	}
	defer buyerConn.Close()

	sellerConn, err := dial(baseURL, sellerAccessToken)
	if err != nil {
		exitf("seller 连接失败: %v", err)
	}
	defer sellerConn.Close()

	if err := buyerConn.WriteJSON(wsFrame{
		"type":       "join",
		"chatTicket": chatTicket,
	}); err != nil {
		exitf("buyer 发送 join 失败: %v", err)
	}

	joinAck, err := waitFrame(buyerConn, deadline, func(frame wsFrame) bool {
		return strings.EqualFold(asString(frame["type"]), "join_ack") || strings.EqualFold(asString(frame["type"]), "error")
	})
	if err != nil {
		exitf("等待 join_ack 失败: %v", err)
	}
	if strings.EqualFold(asString(joinAck["type"]), "error") {
		exitf("join 返回 error: %s", mustJSON(joinAck))
	}
	joinedConversationID := asInt64(joinAck["conversationId"])
	if joinedConversationID <= 0 {
		exitf("join_ack 缺少有效 conversationId: %s", mustJSON(joinAck))
	}
	if conversationID > 0 && joinedConversationID != conversationID {
		exitf("join_ack conversationId 不匹配: expect=%d actual=%d", conversationID, joinedConversationID)
	}

	if err := buyerConn.WriteJSON(wsFrame{
		"type":           "send",
		"conversationId": joinedConversationID,
		"clientMsgId":    clientMsgID,
		"content":        content,
	}); err != nil {
		exitf("buyer 发送 send 失败: %v", err)
	}

	sendAck, err := waitFrame(buyerConn, deadline, func(frame wsFrame) bool {
		frameType := strings.ToLower(strings.TrimSpace(asString(frame["type"])))
		if frameType == "error" {
			return true
		}
		if frameType != "send_ack" {
			return false
		}
		return asString(frame["clientMsgId"]) == clientMsgID
	})
	if err != nil {
		exitf("等待 send_ack 失败: %v", err)
	}
	if strings.EqualFold(asString(sendAck["type"]), "error") {
		exitf("send 返回 error: %s", mustJSON(sendAck))
	}
	sendAckConversationID := asInt64(sendAck["conversationId"])
	if sendAckConversationID != joinedConversationID {
		exitf("send_ack conversationId 不匹配: expect=%d actual=%d", joinedConversationID, sendAckConversationID)
	}
	messageID := asInt64(sendAck["messageId"])
	if messageID <= 0 {
		exitf("send_ack 缺少有效 messageId: %s", mustJSON(sendAck))
	}

	sellerMessage, err := waitFrame(sellerConn, deadline, func(frame wsFrame) bool {
		frameType := strings.ToLower(strings.TrimSpace(asString(frame["type"])))
		if frameType == "error" {
			return true
		}
		if frameType != "chat_message" {
			return false
		}
		if asInt64(frame["conversationId"]) != joinedConversationID {
			return false
		}
		if asString(frame["clientMsgId"]) != clientMsgID {
			return false
		}
		return true
	})
	if err != nil {
		exitf("等待 seller chat_message 失败: %v", err)
	}
	if strings.EqualFold(asString(sellerMessage["type"]), "error") {
		exitf("seller 收到 error: %s", mustJSON(sellerMessage))
	}
	if asString(sellerMessage["content"]) != content {
		exitf("seller chat_message content 不匹配: expect=%q actual=%q", content, asString(sellerMessage["content"]))
	}
	sellerMsgID := asInt64(sellerMessage["messageId"])
	if sellerMsgID != messageID {
		exitf("seller chat_message messageId 不匹配: expect=%d actual=%d", messageID, sellerMsgID)
	}

	fmt.Printf("conversationId=%d messageId=%d clientMsgId=%s\n", joinedConversationID, messageID, clientMsgID)
}

func dial(baseURL, accessToken string) (*websocket.Conn, error) {
	targetURL, err := resolveTargetURL(baseURL, accessToken)
	if err != nil {
		return nil, err
	}
	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	conn, _, err := dialer.Dial(targetURL, nil)
	if err != nil {
		return nil, err
	}
	return conn, nil
}

func resolveTargetURL(baseURL, accessToken string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(baseURL))
	if err != nil {
		return "", err
	}
	q := u.Query()
	q.Set("accessToken", strings.TrimSpace(accessToken))
	u.RawQuery = q.Encode()
	return u.String(), nil
}

func waitFrame(conn *websocket.Conn, deadline time.Time, matcher func(frame wsFrame) bool) (wsFrame, error) {
	for {
		if err := conn.SetReadDeadline(deadline); err != nil {
			return nil, err
		}
		_, payload, err := conn.ReadMessage()
		if err != nil {
			if isTimeout(err) {
				return nil, fmt.Errorf("读取超时")
			}
			return nil, err
		}
		var frame wsFrame
		if err := json.Unmarshal(payload, &frame); err != nil {
			continue
		}
		if matcher(frame) {
			return frame, nil
		}
	}
}

func isTimeout(err error) bool {
	if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
		return true
	}
	return strings.Contains(strings.ToLower(err.Error()), "timeout")
}

func asString(v any) string {
	switch value := v.(type) {
	case string:
		return value
	case []byte:
		return string(value)
	case nil:
		return ""
	default:
		return fmt.Sprintf("%v", value)
	}
}

func asInt64(v any) int64 {
	switch value := v.(type) {
	case int:
		return int64(value)
	case int8:
		return int64(value)
	case int16:
		return int64(value)
	case int32:
		return int64(value)
	case int64:
		return value
	case uint:
		return int64(value)
	case uint8:
		return int64(value)
	case uint16:
		return int64(value)
	case uint32:
		return int64(value)
	case uint64:
		return int64(value)
	case float32:
		return int64(value)
	case float64:
		return int64(value)
	case json.Number:
		parsed, err := value.Int64()
		if err == nil {
			return parsed
		}
		return 0
	case string:
		parsed, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
		if err == nil {
			return parsed
		}
		return 0
	default:
		return 0
	}
}

func mustJSON(v any) string {
	data, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(data)
}

func exitf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "[chat-smoke] "+format+"\n", args...)
	os.Exit(1)
}
