package ai.niriksha.sdk;

/**
 * Thrown when the guard's verdict is {@link GuardAction#BLOCK}.
 *
 * <p>Carries the verdict, so a caller can log the reason, the risk score and the
 * findings without making a second guard call.
 */
public class GuardBlockedException extends NirikshaAIException {

    private static final long serialVersionUID = 1L;

    private final transient GuardVerdict verdict;

    GuardBlockedException(GuardVerdict verdict, String rules) {
        super("blocked by NirikshaAI guard: " + (rules.isEmpty() ? "policy" : rules));
        this.verdict = verdict;
    }

    /** The verdict that produced this block. */
    public GuardVerdict getVerdict() {
        return verdict;
    }
}
