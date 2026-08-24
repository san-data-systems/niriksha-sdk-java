package ai.niriksha.sdk;

/**
 * What the caller should do with the checked text.
 *
 * <p>Ordered from least to most severe, so verdicts can be combined by taking the
 * maximum.
 */
public enum GuardAction {

    /** Nothing was found. Proceed. */
    ALLOW("allow"),

    /**
     * Something was found that is not reliable enough to act on.
     *
     * <p>Proceed, and record it. Blocking on the broad heuristics is what makes
     * people switch a guard off, after which it protects nothing.
     */
    TAG("tag"),

    /**
     * Sensitive content was found and removed.
     *
     * <p>Proceed with {@link GuardVerdict#safeText(String)}. This is not a block: a
     * customer who asked for PII stripping wants their data protected, not their
     * application broken.
     */
    REDACT("redact"),

    /** High-confidence attack. Do not send the text. */
    BLOCK("block");

    private final String wire;

    GuardAction(String wire) {
        this.wire = wire;
    }

    /** The value used on the wire. */
    public String wireValue() {
        return wire;
    }

    /**
     * Parses a wire value.
     *
     * <p>An unrecognised value maps to {@link #ALLOW} — the weakest action — so a
     * server that invents a new one cannot accidentally outrank a block, and a typo
     * cannot silently start blocking traffic.
     */
    static GuardAction fromWire(String value) {
        if (value == null) {
            return ALLOW;
        }
        for (GuardAction a : values()) {
            if (a.wire.equals(value)) {
                return a;
            }
        }
        return ALLOW;
    }
}
