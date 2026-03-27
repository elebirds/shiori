package moe.hhm.shiori.gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientObservationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayHttpClientTracingConfigurationTest.TestConfiguration.class, properties = {
        "management.tracing.enabled=true",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.propagation.type=w3c",
        "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration,org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration",
        "management.defaults.metrics.export.enabled=false",
        "management.otlp.metrics.export.enabled=false",
        "management.logging.export.otlp.enabled=false"
})
class GatewayHttpClientTracingConfigurationTest {

    @Autowired
    @Qualifier("loadBalancedWebClientBuilder")
    private WebClient.Builder builder;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Test
    void shouldPropagateTraceparentHeaderWhenObservationIsActive() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            traceparent.set(request.headers().getFirst("traceparent"));
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).build());
        };

        Observation.createNotStarted("gateway-test", observationRegistry)
                .observe(() -> builder.clone()
                        .exchangeFunction(exchangeFunction)
                        .build()
                        .get()
                        .uri("http://localhost/test")
                        .retrieve()
                        .toBodilessEntity()
                        .block());

        assertThat(traceparent.get()).startsWith("00-");
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ObservationAutoConfiguration.class,
            OpenTelemetrySdkAutoConfiguration.class,
            OpenTelemetryTracingAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            WebClientObservationAutoConfiguration.class
    })
    static class TestConfiguration extends GatewayHttpClientConfiguration {
    }
}
