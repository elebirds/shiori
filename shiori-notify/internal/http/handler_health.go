package notifyhttp

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func (s *Server) handleHealth(c *gin.Context) {
	s.writeJSON(c, http.StatusOK, gin.H{
		"status":  "ok",
		"service": "shiori-notify",
	})
}
