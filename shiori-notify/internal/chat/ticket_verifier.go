package chat

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type TicketVerifierConfig struct {
	Enabled            bool
	Issuer             string
	PublicKeyPEMBase64 string
}

type jwtChatTicketClaims struct {
	BuyerID   any `json:"buyerId"`
	SellerID  any `json:"sellerId"`
	ListingID any `json:"listingId"`
	jwt.RegisteredClaims
}

type RS256TicketVerifier struct {
	enabled bool
	issuer  string
	key     *rsa.PublicKey
}

func NewRS256TicketVerifier(cfg TicketVerifierConfig) (*RS256TicketVerifier, error) {
	if !cfg.Enabled {
		return &RS256TicketVerifier{enabled: false}, nil
	}
	key, err := parseRSAPublicKey(cfg.PublicKeyPEMBase64)
	if err != nil {
		return nil, err
	}
	return &RS256TicketVerifier{
		enabled: true,
		issuer:  strings.TrimSpace(cfg.Issuer),
		key:     key,
	}, nil
}

func (v *RS256TicketVerifier) Verify(ticket string) (ChatTicketClaims, error) {
	if v == nil || !v.enabled {
		return ChatTicketClaims{}, ErrInvalidTicket
	}
	trimmed := strings.TrimSpace(ticket)
	if trimmed == "" {
		return ChatTicketClaims{}, ErrInvalidTicket
	}

	claims := &jwtChatTicketClaims{}
	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
	)
	if v.issuer != "" {
		parser = jwt.NewParser(
			jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
			jwt.WithIssuer(v.issuer),
		)
	}
	parsed, err := parser.ParseWithClaims(trimmed, claims, func(token *jwt.Token) (any, error) {
		if token == nil || token.Method == nil || token.Method.Alg() != jwt.SigningMethodRS256.Alg() {
			return nil, ErrInvalidTicket
		}
		return v.key, nil
	})
	if err != nil || parsed == nil || !parsed.Valid {
		return ChatTicketClaims{}, fmt.Errorf("%w: %v", ErrInvalidTicket, err)
	}

	buyerID, err := toInt64(claims.BuyerID)
	if err != nil {
		return ChatTicketClaims{}, ErrInvalidTicket
	}
	sellerID, err := toInt64(claims.SellerID)
	if err != nil {
		return ChatTicketClaims{}, ErrInvalidTicket
	}
	listingID, err := toInt64(claims.ListingID)
	if err != nil {
		return ChatTicketClaims{}, ErrInvalidTicket
	}
	expiresAt := time.Time{}
	if claims.ExpiresAt != nil {
		expiresAt = claims.ExpiresAt.Time
	}
	if expiresAt.IsZero() || expiresAt.Before(time.Now()) {
		return ChatTicketClaims{}, ErrInvalidTicket
	}
	return ChatTicketClaims{
		BuyerID:   buyerID,
		SellerID:  sellerID,
		ListingID: listingID,
		JTI:       claims.ID,
		ExpiresAt: expiresAt,
	}, nil
}

func parseRSAPublicKey(publicKeyPEMBase64 string) (*rsa.PublicKey, error) {
	trimmed := strings.TrimSpace(publicKeyPEMBase64)
	if trimmed == "" {
		return nil, errors.New("chat ticket public key is required")
	}
	pemBytes, err := base64.StdEncoding.DecodeString(trimmed)
	if err != nil {
		return nil, fmt.Errorf("decode public key pem base64 failed: %w", err)
	}
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, errors.New("invalid public key pem")
	}
	pubAny, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("parse public key failed: %w", err)
	}
	pub, ok := pubAny.(*rsa.PublicKey)
	if !ok {
		return nil, errors.New("public key must be RSA")
	}
	return pub, nil
}

func toInt64(value any) (int64, error) {
	switch v := value.(type) {
	case float64:
		return int64(v), nil
	case int64:
		return v, nil
	case int:
		return int64(v), nil
	case string:
		return strconv.ParseInt(strings.TrimSpace(v), 10, 64)
	default:
		return 0, errors.New("invalid integer claim")
	}
}
