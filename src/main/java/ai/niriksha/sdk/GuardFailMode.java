package ai.niriksha.sdk;

/**
 * What the guard does when it cannot reach the server.
 *
 * <p>Fail-open is the default because a guard outage must not take down the
 * caller's application — but it is never silent: every fall-back logs a warning,
 * sets {@link GuardVerdict#failedOpen()} and increments {@code guard.fail_open}. A
 * silent fail-open is a security hole wearing a reliability costume, because the
 * control appears to work right up until the moment it is needed.
 */
public enum GuardFailMode {

    /** Allow everything. Default. */
    OPEN("open"),

    /**
     * Block everything.
     *
     * <p>Correct for a hard compliance boundary, and a guaranteed outage for
     * everyone else: it converts a guard outage into an application outage.
     */
    CLOSED("closed"),

    /**
     * Allow everything except locally-detectable credentials.
     *
     * <p>The only fail mode that is both safe and survivable, and the one worth
     * using in production. Ten prefix-anchored secret formats are embedded in the
     * SDK, so an outage stops credential exfiltration locally while everything else
     * still flows.
     */
    SECRETS_CLOSED("secrets_closed");

    private final String wire;

    GuardFailMode(String wire) {
        this.wire = wire;
    }

    /** The value used in logs and metric attributes. */
    public String wireValue() {
        return wire;
    }
}
