package ai.niriksha.sdk;

/**
 * A versioned prompt template returned by {@link NirikshaAI#getPrompt}.
 *
 * <p>The {@link #getText()} value has already had all {@code {{variable}}} placeholders
 * substituted server-side when variables were supplied to {@code GetPromptOptions}.
 */
public final class PromptResponse {

    private final String name;
    private final int version;
    private final String text;
    private final String description;

    PromptResponse(String name, int version, String text, String description) {
        this.name        = name;
        this.version     = version;
        this.text        = text;
        this.description = description;
    }

    /** Unique slug identifier for this prompt. */
    public String getName()        { return name; }

    /** Active deployed version number. */
    public int getVersion()        { return version; }

    /** Rendered prompt text with all variables substituted. */
    public String getText()        { return text; }

    /** Human-readable description of what this prompt does. */
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return "PromptResponse{name='" + name + "', version=" + version + "}";
    }
}
