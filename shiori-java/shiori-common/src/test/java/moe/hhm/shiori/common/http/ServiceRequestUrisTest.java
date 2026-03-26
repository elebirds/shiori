package moe.hhm.shiori.common.http;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRequestUrisTest {

    @Test
    void shouldJoinServiceBaseUrlWithRelativePath() {
        URI uri = ServiceRequestUris.resolve("http://shiori-payment-service", "/api/payment/internal/wallets/1/init");

        assertThat(uri).isEqualTo(URI.create("http://shiori-payment-service/api/payment/internal/wallets/1/init"));
    }

    @Test
    void shouldAvoidDuplicateSlashWhenBaseUrlEndsWithSlash() {
        URI uri = ServiceRequestUris.resolve("http://shiori-payment-service/", "/api/payment/internal/wallets/1/init");

        assertThat(uri).isEqualTo(URI.create("http://shiori-payment-service/api/payment/internal/wallets/1/init"));
    }
}
