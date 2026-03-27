package moe.hhm.shiori.order.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientObservationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(classes = OrderRestClientTracingConfigurationTest.TestConfiguration.class, properties = {
        "management.tracing.enabled=true",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.propagation.type=w3c",
        "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration,org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration",
        "management.defaults.metrics.export.enabled=false",
        "management.otlp.metrics.export.enabled=false",
        "management.logging.export.otlp.enabled=false"
})
class OrderRestClientTracingConfigurationTest {

    @Autowired
    @Qualifier("loadBalancedRestClientBuilder")
    private RestClient.Builder builder;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Test
    void shouldPropagateTraceparentHeaderWhenObservationIsActive() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost/test"))
                .andExpect(header("traceparent", startsWith("00-")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Observation.createNotStarted("test", observationRegistry)
                .observe(() -> builder.build()
                        .get()
                        .uri("http://localhost/test")
                        .retrieve()
                        .toBodilessEntity());

        server.verify();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ObservationAutoConfiguration.class,
            OpenTelemetrySdkAutoConfiguration.class,
            OpenTelemetryTracingAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class,
            RestClientAutoConfiguration.class,
            RestClientObservationAutoConfiguration.class
    })
    static class TestConfiguration extends OrderRestClientConfiguration {
    }
}
