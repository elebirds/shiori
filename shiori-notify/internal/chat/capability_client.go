package chat

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type HTTPUserCapabilityChecker struct {
	baseURL string
	client  *http.Client
}

type activeCapabilityResponse struct {
	UserID       int64    `json:"userId"`
	Capabilities []string `json:"capabilities"`
}

func NewHTTPUserCapabilityChecker(baseURL string, timeout time.Duration) *HTTPUserCapabilityChecker {
	trimmedBaseURL := strings.TrimRight(strings.TrimSpace(baseURL), "/")
	if timeout <= 0 {
		timeout = 800 * time.Millisecond
	}
	return &HTTPUserCapabilityChecker{
		baseURL: trimmedBaseURL,
		client: &http.Client{
			Timeout: timeout,
		},
	}
}

func (c *HTTPUserCapabilityChecker) IsBanned(userID int64, capability string) (bool, error) {
	if c == nil || strings.TrimSpace(c.baseURL) == "" {
		return false, nil
	}
	if userID <= 0 {
		return false, ErrInvalidArgument
	}
	capability = strings.TrimSpace(strings.ToUpper(capability))
	if capability == "" {
		return false, ErrInvalidArgument
	}

	path := fmt.Sprintf("/internal/users/%d/capabilities/active", userID)
	u, err := url.Parse(c.baseURL + path)
	if err != nil {
		return false, fmt.Errorf("build capability query url failed: %w", err)
	}
	req, err := http.NewRequest(http.MethodGet, u.String(), nil)
	if err != nil {
		return false, fmt.Errorf("build capability query request failed: %w", err)
	}
	resp, err := c.client.Do(req)
	if err != nil {
		// Dependency outage should not block chat send path.
		return false, nil
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		// Keep chat path available when capability service is unstable or temporarily unavailable.
		return false, nil
	}

	var payload activeCapabilityResponse
	if decodeErr := json.NewDecoder(resp.Body).Decode(&payload); decodeErr != nil {
		// Invalid payload from dependency should not fail chat sending.
		return false, nil
	}

	for _, item := range payload.Capabilities {
		if strings.EqualFold(strings.TrimSpace(item), capability) {
			return true, nil
		}
	}
	return false, nil
}
