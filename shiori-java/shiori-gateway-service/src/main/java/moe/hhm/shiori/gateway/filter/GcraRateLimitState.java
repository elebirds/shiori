package moe.hhm.shiori.gateway.filter;

final class GcraRateLimitState {

    private final long emissionIntervalMicros;
    private final long burstToleranceMicros;
    private final long theoreticalArrivalMicros;
    private final long ttlMillis;

    private GcraRateLimitState(long emissionIntervalMicros,
                               long burstToleranceMicros,
                               long theoreticalArrivalMicros,
                               long ttlMillis) {
        this.emissionIntervalMicros = emissionIntervalMicros;
        this.burstToleranceMicros = burstToleranceMicros;
        this.theoreticalArrivalMicros = theoreticalArrivalMicros;
        this.ttlMillis = ttlMillis;
    }

    static GcraRateLimitState initial(GatewayRateLimitRule rule) {
        long windowMicros = rule.windowMillis() * 1000L;
        long emissionIntervalMicros = ceilDiv(windowMicros, rule.limit());
        long burstToleranceMicros = emissionIntervalMicros * Math.max(0, rule.limit() - 1L);
        return new GcraRateLimitState(
                emissionIntervalMicros,
                burstToleranceMicros,
                -1L,
                Math.max(1L, rule.windowMillis())
        );
    }

    Evaluation evaluate(long nowMicros) {
        long tat = theoreticalArrivalMicros >= 0 ? theoreticalArrivalMicros : nowMicros;
        long allowAtMicros = tat - burstToleranceMicros;
        if (nowMicros < allowAtMicros) {
            return new Evaluation(false, allowAtMicros - nowMicros, this);
        }
        long nextTatMicros = Math.max(nowMicros, tat) + emissionIntervalMicros;
        return new Evaluation(
                true,
                0L,
                new GcraRateLimitState(emissionIntervalMicros, burstToleranceMicros, nextTatMicros, ttlMillis)
        );
    }

    long emissionIntervalMicros() {
        return emissionIntervalMicros;
    }

    long burstToleranceMicros() {
        return burstToleranceMicros;
    }

    long ttlMillis() {
        return ttlMillis;
    }

    private static long ceilDiv(long dividend, long divisor) {
        return Math.floorDiv(dividend + divisor - 1L, divisor);
    }

    record Evaluation(boolean allowed, long retryAfterMicros, GcraRateLimitState nextState) {
    }
}
