package moe.hhm.shiori.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.hmac-secret=test-secret-test-secret-test-secret-1234",
        "security.jwt.issuer=shiori"
})
class GatewaySecurityIntegrationTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    @LocalServerPort
    private int port;

    @Test
    void shouldReturn401WhenNoToken() {
        webTestClient().get()
                .uri("/api/user/profile")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(20002)
                .jsonPath("$.message").isEqualTo("网关认证失败")
                .jsonPath("$.timestamp").exists();
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldReturn401WhenTokenInvalid() {
        webTestClient().get()
                .uri("/api/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(20002)
                .jsonPath("$.message").isEqualTo("网关认证失败");
    }

    @Test
    void shouldReturn403WhenNoAdminRole() throws Exception {
        String token = createToken("u1001", List.of("USER"));
        webTestClient().get()
                .uri("/api/admin/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(20003)
                .jsonPath("$.message").isEqualTo("网关无权限访问");
    }

    @Test
    void shouldPassSecurityWhenTokenValid() throws Exception {
        String token = createToken("u1001", List.of("USER"));
        webTestClient().get()
                .uri("/api/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    private String createToken(String userId, List<String> roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer("shiori")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(1800)))
                .claim("roles", roles)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }
}
