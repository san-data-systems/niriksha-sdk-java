package ai.niriksha.sdk;

/**
 * Metadata for an LLM tool/function invocation.
 *
 * <p>Use {@link #builder()} to construct instances:
 * <pre>{@code
 * ToolCall call = ToolCall.builder()
 *     .toolName("search_logs")
 *     .callId("call_abc123")
 *     .input("{\"query\":\"error rate\",\"window\":\"5m\"}")
 *     .output("{\"count\":42}")
 *     .build();
 * SpanHelpers.recordToolCall(span, call);
 * }</pre>
 */
public final class ToolCall {

    private final String toolName;
    private final String callId;
    private final String input;  // JSON string, may be null
    private final String output; // JSON string, may be null

    private ToolCall(Builder b) {
        this.toolName = b.toolName;
        this.callId   = b.callId;
        this.input    = b.input;
        this.output   = b.output;
    }

    /** Name of the tool or function being called. */
    public String getToolName() {
        return toolName;
    }

    /** Unique call identifier assigned by the LLM (e.g. OpenAI's {@code tool_call_id}). */
    public String getCallId() {
        return callId;
    }

    /**
     * JSON-serialised input arguments, or {@code null} if not captured.
     * Avoid recording arguments that may contain PII; use {@link PiiRedactor} first.
     */
    public String getInput() {
        return input;
    }

    /**
     * JSON-serialised output returned by the tool, or {@code null} if not captured.
     * Avoid recording output that may contain PII; use {@link PiiRedactor} first.
     */
    public String getOutput() {
        return output;
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link ToolCall}. */
    public static final class Builder {

        private String toolName;
        private String callId;
        private String input;
        private String output;

        private Builder() {}

        /** Sets the tool name. Required. */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /** Sets the unique call identifier. Required. */
        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        /** Sets the JSON-serialised input arguments. Optional. */
        public Builder input(String input) {
            this.input = input;
            return this;
        }

        /** Sets the JSON-serialised output returned by the tool. Optional. */
        public Builder output(String output) {
            this.output = output;
            return this;
        }

        /**
         * Builds the {@link ToolCall}.
         *
         * @throws IllegalStateException if {@code toolName} or {@code callId} is blank or null
         */
        public ToolCall build() {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalStateException("ToolCall: toolName is required");
            }
            if (callId == null || callId.isBlank()) {
                throw new IllegalStateException("ToolCall: callId is required");
            }
            return new ToolCall(this);
        }
    }
}
