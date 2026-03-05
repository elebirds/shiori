package moe.hhm.shiori.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GatewayGovernanceMetrics {

    private static final String GOVERNANCE_TOTAL = "shiori_gateway_governance_total";

    private final MeterRegistry meterRegistry;

    public GatewayGovernanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incRateLimit(String endpoint, String decision) {
        meterRegistry.counter(
                GOVERNANCE_TOTAL,
                "type", "rate_limit",
                "endpoint", sanitize(endpoint),
                "decision", sanitize(decision)
        ).increment();
    }

    public void incCapabilityCheck(String capability, String decision) {
        meterRegistry.counter(
                GOVERNANCE_TOTAL,
                "type", "capability_ban",
                "endpoint", sanitize(capability),
                "decision", sanitize(decision)
        ).increment();
    }

    public void incAuthzDecision(String permissionCode, String decision) {
        meterRegistry.counter(
                "authz_decision_total",
                "permission", sanitize(permissionCode),
                "decision", sanitize(decision)
        ).increment();
    }

    public void incAuthzDegradedAllow(String reason) {
        meterRegistry.counter(
                "authz_degraded_allow_total",
                "reason", sanitize(reason)
        ).increment();
    }

    public void observeAuthzSnapshotStaleSeconds(double seconds) {
        meterRegistry.summary("authz_snapshot_stale_seconds").record(Math.max(0D, seconds));
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }
}
