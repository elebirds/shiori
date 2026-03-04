package auth

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/golang-jwt/jwt/v5"
)

var (
	ErrMissingToken = errors.New("missing access token")
	ErrInvalidToken = errors.New("invalid access token")
)

type JWTVerifier struct {
	enabled bool
	secret  []byte
	issuer  string
}

func NewJWTVerifier(enabled bool, hmacSecret, issuer string) (*JWTVerifier, error) {
	trimmedIssuer := strings.TrimSpace(issuer)
	if !enabled {
		return &JWTVerifier{
			enabled: false,
			secret:  nil,
			issuer:  trimmedIssuer,
		}, nil
	}

	trimmedSecret := strings.TrimSpace(hmacSecret)
	if trimmedSecret == "" {
		return nil, errors.New("notify auth is enabled but JWT HMAC secret is empty")
	}

	return &JWTVerifier{
		enabled: true,
		secret:  []byte(trimmedSecret),
		issuer:  trimmedIssuer,
	}, nil
}

func (v *JWTVerifier) Enabled() bool {
	return v != nil && v.enabled
}

func (v *JWTVerifier) ParseUserIDFromToken(rawToken string) (string, error) {
	if !v.Enabled() {
		return "", nil
	}

	tokenText := strings.TrimSpace(rawToken)
	if tokenText == "" {
		return "", ErrMissingToken
	}

	options := []jwt.ParserOption{
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
	}
	if v.issuer != "" {
		options = append(options, jwt.WithIssuer(v.issuer))
	}

	token, err := jwt.Parse(tokenText, func(token *jwt.Token) (any, error) {
		if token == nil {
			return nil, ErrInvalidToken
		}
		if token.Method == nil || token.Method.Alg() != jwt.SigningMethodHS256.Alg() {
			return nil, ErrInvalidToken
		}
		return v.secret, nil
	}, options...)
	if err != nil || token == nil || !token.Valid {
		return "", fmt.Errorf("%w: %v", ErrInvalidToken, err)
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", ErrInvalidToken
	}

	for _, key := range []string{"uid", "sub", "userId"} {
		claimValue, exists := claims[key]
		if !exists {
			continue
		}
		userID := claimToString(claimValue)
		if userID != "" {
			return userID, nil
		}
	}

	return "", ErrInvalidToken
}

func claimToString(value any) string {
	switch v := value.(type) {
	case string:
		return strings.TrimSpace(v)
	case float64:
		return strconv.FormatInt(int64(v), 10)
	case int64:
		return strconv.FormatInt(v, 10)
	case int:
		return strconv.Itoa(v)
	case json.Number:
		return strings.TrimSpace(v.String())
	default:
		return ""
	}
}
