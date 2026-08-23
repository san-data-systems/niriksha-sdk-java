package ai.niriksha.sdk;

import java.util.List;

/** The guard's decision about one piece of text. */
public final class GuardVerdict {

    private final GuardAction action;
    private final List<GuardFinding> findings;
    private final List<String> reasons;
    private final String redacted;
    private final int riskScore;
    private final String riskSeverity;
    private final String policySource;
    private final boolean policyEnforced;
    private final boolean failedOpen;

    GuardVerdict(GuardAction action, List<GuardFinding> findings, List<String> reasons,
            String redacted, int riskScore, String riskSeverity,
            String policySource, boolean policyEnforced, boolean failedOpen) {
        this.action         = action != null ? action : GuardAction.ALLOW;
        this.findings       = findings != null ? List.copyOf(findings) : List.of();
        this.reasons        = reasons != null ? List.copyOf(reasons) : List.of();
        this.redacted       = redacted != null ? redacted : "";
        this.riskScore      = riskScore;
        this.riskSeverity   = riskSeverity != null ? riskSeverity : "";
        this.policySource   = policySource != null ? policySource : "";
        this.policyEnforced = policyEnforced;
        this.failedOpen     = failedOpen;
    }

    /** What the caller should do with the text. */
    public GuardAction getAction() {
        return action;
    }

    /** Every detection, never {@code null}. */
    public List<GuardFinding> getFindings() {
        return findings;
    }

    /**
     * Low-precision detections that were recorded but did not influence the action.
     *
     * <p>Surfaced so an operator can see what the guard noticed without it becoming
     * a block.
     */
    public List<String> getReasons() {
        return reasons;
    }

    /** The rewritten text. Non-empty only when the action is {@link GuardAction#REDACT}. */
    public String getRedacted() {
        return redacted;
    }

    /** 0..100. {@code min(100, sum(severity_weight * confidence))}. */
    public int getRiskScore() {
        return riskScore;
    }

    /** The band of {@link #getRiskScore()}: low, medium, high or critical. */
    public String getRiskSeverity() {
        return riskSeverity;
    }

    /**
     * {@code project} when an operator's stored AIDR policy was applied,
     * {@code default} when the product default was.
     *
     * <p>Only {@code project} is binding: a monitor-mode call cannot lift a block
     * that an operator's own policy mandates.
     */
    public String getPolicySource() {
        return policySource;
    }

    /**
     * Whether the applied block is mandated by the operator's policy rather than by
     * rule precision alone.
     *
     * <p>Lets a caller receiving an unexpected block tell a detection change from a
     * policy change.
     */
    public boolean isPolicyEnforced() {
        return policyEnforced;
    }

    /**
     * Whether the guard was unreachable and the configured fail mode decided the
     * outcome.
     *
     * <p>Never silently {@code true}: a warning is logged whenever it is set.
     */
    public boolean failedOpen() {
        return failedOpen;
    }

    /** Convenience for {@code getAction() == GuardAction.BLOCK}. */
    public boolean isBlocked() {
        return action == GuardAction.BLOCK;
    }

    /**
     * Returns the text that is safe to send: the redaction when there was one, the
     * original otherwise.
     *
     * <p>Exists so no caller has to write the equivalent conditional, which is easy
     * to get wrong in the direction that forwards the secret. Also returns the
     * original when a malformed redact verdict carries no text, rather than silently
     * substituting an empty prompt.
     */
    public String safeText(String original) {
        return action == GuardAction.REDACT && !redacted.isEmpty() ? redacted : original;
    }

    @Override
    public String toString() {
        return "GuardVerdict{action=" + action + ", riskScore=" + riskScore
                + ", findings=" + findings.size() + "}";
    }
}
