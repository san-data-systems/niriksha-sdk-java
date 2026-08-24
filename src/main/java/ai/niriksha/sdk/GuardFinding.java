package ai.niriksha.sdk;

/** One detection reported by the guard. */
public final class GuardFinding {

    private final String category;
    private final String severity;
    private final String rule;
    private final double confidence;
    private final int start;
    private final int end;
    private final boolean local;

    GuardFinding(String category, String severity, String rule, double confidence,
            int start, int end, boolean local) {
        this.category   = category != null ? category : "";
        this.severity   = severity != null ? severity : "";
        this.rule       = rule != null ? rule : "";
        this.confidence = confidence;
        this.start      = start;
        this.end        = end;
        this.local      = local;
    }

    /** {@code prompt_injection}, {@code jailbreak}, {@code secret}, {@code pii} or {@code tool_abuse}. */
    public String getCategory() {
        return category;
    }

    /** {@code low}, {@code medium}, {@code high} or {@code critical}. */
    public String getSeverity() {
        return severity;
    }

    /** The name of the rule that matched. */
    public String getRule() {
        return rule;
    }

    /** How much the rule is trusted, 0..1. Weights the risk score. */
    public double getConfidence() {
        return confidence;
    }

    /** Byte offset of the match start, for exact redaction. */
    public int getStart() {
        return start;
    }

    /** Byte offset of the match end, exclusive. */
    public int getEnd() {
        return end;
    }

    /**
     * Whether this came from the SDK's embedded patterns rather than the server.
     *
     * <p>Only set when the guard was unreachable and the fail mode is
     * {@link GuardFailMode#SECRETS_CLOSED}.
     */
    public boolean isLocal() {
        return local;
    }

    @Override
    public String toString() {
        return "GuardFinding{rule='" + rule + "', severity='" + severity + "'}";
    }
}
