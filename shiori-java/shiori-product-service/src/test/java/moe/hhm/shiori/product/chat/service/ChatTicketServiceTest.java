package moe.hhm.shiori.product.chat.service;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.chat.config.ChatTicketProperties;
import moe.hhm.shiori.product.chat.dto.ChatTicketResponse;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatTicketServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Test
    void shouldIssueTicketWithExpectedClaims() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        ChatTicketProperties properties = new ChatTicketProperties();
        properties.setIssuer("chat-issuer");
        properties.setTtlSeconds(300);
        properties.setPrivateKeyPemBase64(toPrivateKeyPemBase64(keyPair));

        when(productMapper.findOnSaleOwnerUserIdByProductId(101L)).thenReturn(2002L);
        ChatTicketService service = new ChatTicketService(properties, productMapper);

        Instant before = Instant.now();
        ChatTicketResponse response = service.issueTicket(101L, 1001L);
        Instant after = Instant.now();

        assertThat(response.buyerId()).isEqualTo(1001L);
        assertThat(response.sellerId()).isEqualTo(2002L);
        assertThat(response.listingId()).isEqualTo(101L);
        assertThat(response.jti()).isNotBlank();
        assertThat(response.ticket()).isNotBlank();

        SignedJWT signedJWT = SignedJWT.parse(response.ticket());
        JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
        assertThat(signedJWT.verify(verifier)).isTrue();

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("chat-issuer");
        assertThat(claims.getLongClaim("buyerId")).isEqualTo(1001L);
        assertThat(claims.getLongClaim("sellerId")).isEqualTo(2002L);
        assertThat(claims.getLongClaim("listingId")).isEqualTo(101L);
        assertThat(claims.getJWTID()).isEqualTo(response.jti());
        assertThat(claims.getIssueTime().toInstant()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
        assertThat(claims.getExpirationTime().toInstant()).isAfter(before.plusSeconds(250));
    }

    @Test
    void shouldRejectSelfChat() {
        KeyPair keyPair = generateRsaKeyPair();
        ChatTicketProperties properties = new ChatTicketProperties();
        properties.setPrivateKeyPemBase64(toPrivateKeyPemBase64(keyPair));
        when(productMapper.findOnSaleOwnerUserIdByProductId(101L)).thenReturn(1001L);
        ChatTicketService service = new ChatTicketService(properties, productMapper);

        assertThatThrownBy(() -> service.issueTicket(101L, 1001L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAM));
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toPrivateKeyPemBase64(KeyPair keyPair) {
        byte[] der = keyPair.getPrivate().getEncoded();
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }
}
