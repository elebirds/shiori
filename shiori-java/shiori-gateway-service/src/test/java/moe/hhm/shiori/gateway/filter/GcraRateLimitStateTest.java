package moe.hhm.shiori.gateway.filter;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GcraRateLimitStateTest {

    @Test
    void shouldRecoverAtEmissionIntervalAfterBurst() {
        GatewayRateLimitRule rule = new GatewayRateLimitRule("order_pay", 5, Duration.ofSeconds(1));
        GcraRateLimitState state = GcraRateLimitState.initial(rule);

        long[] burst = {900_000, 920_000, 940_000, 960_000, 980_000};
        for (long nowMicros : burst) {
            GcraRateLimitState.Evaluation evaluation = state.evaluate(nowMicros);
            assertThat(evaluation.allowed()).isTrue();
            state = evaluation.nextState();
        }

        GcraRateLimitState.Evaluation tooEarly = state.evaluate(1_099_000);
        assertThat(tooEarly.allowed()).isFalse();
        assertThat(tooEarly.retryAfterMicros()).isEqualTo(1_000);

        GcraRateLimitState.Evaluation recovered = state.evaluate(1_100_000);
        assertThat(recovered.allowed()).isTrue();
        assertThat(recovered.retryAfterMicros()).isZero();
    }
}
