package chat

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestRS256TicketVerifierVerify(t *testing.T) {
	privateKey, publicKey := mustGenerateRSAKeyPair(t)
	verifier, err := NewRS256TicketVerifier(TicketVerifierConfig{
		Enabled:            true,
		Issuer:             "chat-issuer",
		PublicKeyPEMBase64: toPublicPEMBase64(publicKey),
	})
	if err != nil {
		t.Fatalf("new verifier failed: %v", err)
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss":       "chat-issuer",
		"exp":       time.Now().Add(5 * time.Minute).Unix(),
		"jti":       "jti-1",
		"buyerId":   float64(1001),
		"sellerId":  float64(2002),
		"listingId": float64(101),
	})
	tokenText, signErr := token.SignedString(privateKey)
	if signErr != nil {
		t.Fatalf("sign token failed: %v", signErr)
	}

	claims, verifyErr := verifier.Verify(tokenText)
	if verifyErr != nil {
		t.Fatalf("verify failed: %v", verifyErr)
	}
	if claims.BuyerID != 1001 || claims.SellerID != 2002 || claims.ListingID != 101 {
		t.Fatalf("unexpected claims: %+v", claims)
	}
	if claims.JTI != "jti-1" {
		t.Fatalf("unexpected jti: %s", claims.JTI)
	}
}

func TestRS256TicketVerifierRejectInvalidIssuer(t *testing.T) {
	privateKey, publicKey := mustGenerateRSAKeyPair(t)
	verifier, err := NewRS256TicketVerifier(TicketVerifierConfig{
		Enabled:            true,
		Issuer:             "chat-issuer",
		PublicKeyPEMBase64: toPublicPEMBase64(publicKey),
	})
	if err != nil {
		t.Fatalf("new verifier failed: %v", err)
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss":       "wrong-issuer",
		"exp":       time.Now().Add(5 * time.Minute).Unix(),
		"buyerId":   float64(1001),
		"sellerId":  float64(2002),
		"listingId": float64(101),
	})
	tokenText, signErr := token.SignedString(privateKey)
	if signErr != nil {
		t.Fatalf("sign token failed: %v", signErr)
	}

	if _, verifyErr := verifier.Verify(tokenText); verifyErr == nil {
		t.Fatalf("expected verify error")
	}
}

func TestRS256TicketVerifierRejectExpired(t *testing.T) {
	privateKey, publicKey := mustGenerateRSAKeyPair(t)
	verifier, err := NewRS256TicketVerifier(TicketVerifierConfig{
		Enabled:            true,
		Issuer:             "chat-issuer",
		PublicKeyPEMBase64: toPublicPEMBase64(publicKey),
	})
	if err != nil {
		t.Fatalf("new verifier failed: %v", err)
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss":       "chat-issuer",
		"exp":       time.Now().Add(-time.Minute).Unix(),
		"buyerId":   float64(1001),
		"sellerId":  float64(2002),
		"listingId": float64(101),
	})
	tokenText, signErr := token.SignedString(privateKey)
	if signErr != nil {
		t.Fatalf("sign token failed: %v", signErr)
	}

	if _, verifyErr := verifier.Verify(tokenText); verifyErr == nil {
		t.Fatalf("expected verify error")
	}
}

func mustGenerateRSAKeyPair(t *testing.T) (*rsa.PrivateKey, *rsa.PublicKey) {
	t.Helper()
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate rsa key failed: %v", err)
	}
	return privateKey, &privateKey.PublicKey
}

func toPublicPEMBase64(publicKey *rsa.PublicKey) string {
	der, _ := x509.MarshalPKIXPublicKey(publicKey)
	pemBytes := pem.EncodeToMemory(&pem.Block{
		Type:  "PUBLIC KEY",
		Bytes: der,
	})
	return base64.StdEncoding.EncodeToString(pemBytes)
}
