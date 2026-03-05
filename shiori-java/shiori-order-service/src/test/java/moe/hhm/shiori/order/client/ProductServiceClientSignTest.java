package moe.hhm.shiori.order.client;

import java.util.List;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.order.config.ProductClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServiceClientSignTest {

    @Test
    void shouldWriteSignedHeadersWithCanonicalPayload() {
        ProductClientProperties properties = new ProductClientProperties();
        GatewaySignProperties gatewaySignProperties = new GatewaySignProperties();
        gatewaySignProperties.setInternalSecret("test-gateway-sign-secret-32-bytes-0001");

        ProductServiceClient client = new ProductServiceClient(
                RestClient.builder(),
                properties,
                gatewaySignProperties,
                new ObjectMapper()
        );

        HttpHeaders headers = new HttpHeaders();
        client.fillSignedHeaders(headers, "GET", "/api/v2/product/products/1", "foo=bar", 1001L, List.of("ROLE_USER"));

        String ts = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_TS);
        String sign = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN);
        String nonce = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE);
        assertThat(headers.getFirst(GatewaySignVerifyFilter.HEADER_USER_ID)).isEqualTo("1001");
        assertThat(headers.getFirst(GatewaySignVerifyFilter.HEADER_USER_ROLES)).isEqualTo("ROLE_USER");
        assertThat(ts).isNotBlank();
        assertThat(sign).isNotBlank();
        assertThat(nonce).isNotBlank();

        String canonical = GatewaySignUtils.buildCanonicalString(
                "GET", "/api/v2/product/products/1", "foo=bar", "1001", "ROLE_USER", ts, nonce);
        String expected = GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical);
        assertThat(sign).isEqualTo(expected);
    }
}
