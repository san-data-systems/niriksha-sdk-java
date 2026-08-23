package ai.niriksha.sdk;

import java.util.List;

/** The result of a batch guard call. */
public final class GuardBatchResult {

    private final GuardAction action;
    private final List<GuardVerdict> verdicts;

    GuardBatchResult(GuardAction action, List<GuardVerdict> verdicts) {
        this.action   = action != null ? action : GuardAction.ALLOW;
        this.verdicts = verdicts != null ? List.copyOf(verdicts) : List.of();
    }

    /**
     * The most severe verdict in the set.
     *
     * <p>One blocked message means the conversation must not be sent, so this — not
     * any individual verdict — is what a caller acts on.
     */
    public GuardAction getAction() {
        return action;
    }

    /** The per-item verdicts, in request order. */
    public List<GuardVerdict> getVerdicts() {
        return verdicts;
    }
}
