package ai.niriksha.sdk;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional parameters for {@link NirikshaAI#getPrompt(String, GetPromptOptions)}.
 *
 * <pre>{@code
 * GetPromptOptions opts = GetPromptOptions.builder()
 *     .version(3)
 *     .variable("product_name", "Widget Pro")
 *     .variable("category", "Electronics")
 *     .build();
 *
 * PromptResponse prompt = NirikshaAI.getPrompt("product-description", opts);
 * }</pre>
 */
public final class GetPromptOptions {

    private final Integer version;
    private final Map<String, String> variables;

    private GetPromptOptions(Builder b) {
        this.version   = b.version;
        this.variables = Collections.unmodifiableMap(new HashMap<>(b.variables));
    }

    /** Specific version to fetch, or {@code null} for the latest deployed version. */
    public Integer getVersion() {
        return version;
    }

    /** Variable substitutions applied server-side ({@code {{key}}} → value). */
    public Map<String, String> getVariables() {
        return variables;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer version;
        private final Map<String, String> variables = new HashMap<>();

        private Builder() {}

        /** Pin to a specific version. Omit to use the latest deployed version. */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /** Add a single template variable substitution. */
        public Builder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        /** Set all template variables at once. */
        public Builder variables(Map<String, String> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public GetPromptOptions build() {
            return new GetPromptOptions(this);
        }
    }
}
