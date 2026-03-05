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

const (
	capabilityCheckMaxAttempts  = 2
	capabilityCheckRetryBackoff = 60 * time.Millisecond
)

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
	var lastErr error
	for attempt := 1; attempt <= capabilityCheckMaxAttempts; attempt++ {
		resp, doErr := c.client.Do(req)
		if doErr != nil {
			lastErr = &ErrCapabilityCheckFailed{
				Reason: "transport_error",
				Cause:  doErr,
			}
			if attempt < capabilityCheckMaxAttempts {
				time.Sleep(capabilityCheckRetryBackoff)
				continue
			}
			return false, lastErr
		}

		var payload activeCapabilityResponse
		decodeErr := json.NewDecoder(resp.Body).Decode(&payload)
		_ = resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			lastErr = &ErrCapabilityCheckFailed{
				Reason:     "upstream_status",
				StatusCode: resp.StatusCode,
			}
			if resp.StatusCode >= 500 && attempt < capabilityCheckMaxAttempts {
				time.Sleep(capabilityCheckRetryBackoff)
				continue
			}
			return false, lastErr
		}
		if decodeErr != nil {
			return false, &ErrCapabilityCheckFailed{
				Reason: "decode_error",
				Cause:  decodeErr,
			}
		}

		for _, item := range payload.Capabilities {
			if strings.EqualFold(strings.TrimSpace(item), capability) {
				return true, nil
			}
		}
		return false, nil
	}
	if lastErr != nil {
		return false, lastErr
	}
	return false, &ErrCapabilityCheckFailed{Reason: "unknown"}
}
