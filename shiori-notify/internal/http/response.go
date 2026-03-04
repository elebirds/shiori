package notifyhttp

import (
	"time"

	"github.com/gin-gonic/gin"
)

func (s *Server) writeJSON(c *gin.Context, status int, payload gin.H) {
	if payload == nil {
		payload = gin.H{}
	}
	if _, exists := payload["timestamp"]; !exists {
		payload["timestamp"] = time.Now().UnixMilli()
	}
	c.JSON(status, payload)
}
