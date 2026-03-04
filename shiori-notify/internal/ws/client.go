package ws

import (
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

type Client struct {
	conn         *websocket.Conn
	writeTimeout time.Duration
	pingInterval time.Duration

	mu      sync.Mutex
	closeMu sync.Once
	closeCh chan struct{}
}

func NewClient(conn *websocket.Conn, writeTimeout, pingInterval time.Duration) *Client {
	if writeTimeout <= 0 {
		writeTimeout = 5 * time.Second
	}

	return &Client{
		conn:         conn,
		writeTimeout: writeTimeout,
		pingInterval: pingInterval,
		closeCh:      make(chan struct{}),
	}
}

func (c *Client) Run(onMessage func(payload []byte), onClose func()) {
	defer func() {
		if onClose != nil {
			onClose()
		}
		_ = c.Close()
	}()

	if c.pingInterval > 0 {
		go c.pingLoop()
		_ = c.conn.SetReadDeadline(time.Now().Add(2 * c.pingInterval))
		c.conn.SetPongHandler(func(string) error {
			return c.conn.SetReadDeadline(time.Now().Add(2 * c.pingInterval))
		})
	}

	for {
		select {
		case <-c.closeCh:
			return
		default:
		}

		_, payload, err := c.conn.ReadMessage()
		if err != nil {
			return
		}
		if onMessage != nil {
			onMessage(payload)
		}
	}
}

func (c *Client) Send(payload []byte) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if err := c.conn.SetWriteDeadline(time.Now().Add(c.writeTimeout)); err != nil {
		return err
	}
	return c.conn.WriteMessage(websocket.TextMessage, payload)
}

func (c *Client) Close() error {
	var err error
	c.closeMu.Do(func() {
		close(c.closeCh)
		c.mu.Lock()
		defer c.mu.Unlock()
		err = c.conn.Close()
	})
	return err
}

func (c *Client) pingLoop() {
	ticker := time.NewTicker(c.pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-c.closeCh:
			return
		case <-ticker.C:
			c.mu.Lock()
			_ = c.conn.SetWriteDeadline(time.Now().Add(c.writeTimeout))
			err := c.conn.WriteMessage(websocket.PingMessage, nil)
			c.mu.Unlock()
			if err != nil {
				_ = c.Close()
				return
			}
		}
	}
}
