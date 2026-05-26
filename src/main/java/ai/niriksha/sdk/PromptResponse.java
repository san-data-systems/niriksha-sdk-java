package ai.niriksha.sdk;

import java.util.List;

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
    private final List<String> tags;
    private final String createdAt;
    private final String updatedAt;

    PromptResponse(String name, int version, String text, String description,
            List<String> tags, String createdAt, String updatedAt) {
        this.name        = name;
        this.version     = version;
        this.text        = text;
        this.description = description;
        this.tags        = tags != null ? List.copyOf(tags) : List.of();
        this.createdAt   = createdAt != null ? createdAt : "";
        this.updatedAt   = updatedAt != null ? updatedAt : "";
    }

    /** Unique slug identifier for this prompt. */
    public String getName() {
        return name;
    }

    /** Active deployed version number. */
    public int getVersion() {
        return version;
    }

    /** Rendered prompt text with all variables substituted. */
    public String getText() {
        return text;
    }

    /** Human-readable description of what this prompt does. */
    public String getDescription() {
        return description;
    }

    /** Tags associated with this prompt. Never {@code null}; may be empty. */
    public List<String> getTags() {
        return tags;
    }

    /** ISO-8601 creation timestamp, or empty string if not provided. */
    public String getCreatedAt() {
        return createdAt;
    }

    /** ISO-8601 last-updated timestamp, or empty string if not provided. */
    public String getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PromptResponse{name='" + name + "', version=" + version + "}";
    }
}
