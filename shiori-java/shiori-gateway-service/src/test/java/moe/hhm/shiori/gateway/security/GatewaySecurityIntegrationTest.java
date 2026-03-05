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
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.hmac-secret=test-secret-test-secret-test-secret-1234",
        "security.jwt.issuer=shiori",
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001"
})
class GatewaySecurityIntegrationTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    @LocalServerPort
    private int port;

    @Test
    void shouldReturn401WhenNoToken() {
        webTestClient().get()
                .uri("/api/user/me")
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
                .uri("/api/user/me")
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
    void shouldReturn403WhenNoAdminRoleOnV2AdminPath() throws Exception {
        String token = createToken("u1001", List.of("USER"));
        webTestClient().get()
                .uri("/api/v2/admin/dashboard")
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
        HttpStatusCode status = webTestClient().get()
                .uri("/api/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnAuthLoginPath() {
        HttpStatusCode status = webTestClient().post()
                .uri("/api/user/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"username\":\"alice\",\"password\":\"pwd\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnAuthRegisterPath() {
        HttpStatusCode status = webTestClient().post()
                .uri("/api/user/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"username\":\"alice\",\"password\":\"newPwd123\",\"nickname\":\"Alice\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnAuthRefreshPath() {
        HttpStatusCode status = webTestClient().post()
                .uri("/api/user/auth/refresh")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"refreshToken\":\"x\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnAuthLogoutPath() {
        HttpStatusCode status = webTestClient().post()
                .uri("/api/user/auth/logout")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"refreshToken\":\"x\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnProductGetPath() {
        HttpStatusCode status = webTestClient().get()
                .uri("/api/v2/product/products")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnPublicUserProfilePath() {
        HttpStatusCode status = webTestClient().get()
                .uri("/api/user/profiles/U202603030001")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnPublicAvatarPath() {
        HttpStatusCode status = webTestClient().get()
                .uri("/api/user/media/avatar/avatar_1_202603_abc.jpg")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
    }

    @Test
    void shouldAllowAnonymousOnUserProductsPath() {
        HttpStatusCode status = webTestClient().get()
                .uri("/api/v2/product/users/1001/products")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
        assertThat(status.value()).isNotEqualTo(403);
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
