package ai.niriksha.sdk;

/** One entry in a batch guard request. */
public final class GuardBatchItem {

    private final String text;
    private final String direction;

    private GuardBatchItem(String text, String direction) {
        this.text      = text;
        this.direction = direction;
    }

    /** An input item — a prompt heading to the model. */
    public static GuardBatchItem input(String text) {
        return new GuardBatchItem(text, "input");
    }

    /** An output item — a completion coming back. */
    public static GuardBatchItem output(String text) {
        return new GuardBatchItem(text, "output");
    }

    public String getText() {
        return text;
    }

    /** {@code input} or {@code output}. */
    public String getDirection() {
        return direction;
    }
}
