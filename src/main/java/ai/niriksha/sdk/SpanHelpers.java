package ai.niriksha.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;

/**
 * Utility methods for enriching OpenTelemetry spans with LLM, RAG, and tool-call metadata.
 *
 * <p>All methods are null-safe for the {@code Span} argument — calls on
 * {@link Span#getInvalid()} are silently no-ops as per the OTel API contract.
 *
 * <p>Example:
 * <pre>{@code
 * Span span = tracer.spanBuilder("llm.chat").startSpan();
 * try (Scope scope = span.makeCurrent()) {
 *     SpanHelpers.recordConversation(span, conversationId, sessionId, turnIndex);
 *     SpanHelpers.recordRagChunk(span, chunk);
 *     SpanHelpers.recordToolCall(span, call);
 * } finally {
 *     span.end();
 * }
 * }</pre>
 */
public final class SpanHelpers {

    // -- Conversation attribute keys --
    private static final AttributeKey<String> KEY_CONV_ID    = AttributeKey.stringKey("llm.conversation.id");
    private static final AttributeKey<String> KEY_SESSION_ID = AttributeKey.stringKey("llm.session.id");
    private static final AttributeKey<Long>   KEY_TURN_INDEX = AttributeKey.longKey("llm.turn.index");

    // -- RAG event attribute keys --
    private static final AttributeKey<String> KEY_RAG_CHUNK_ID = AttributeKey.stringKey("rag.chunk.id");
    private static final AttributeKey<String> KEY_RAG_SOURCE   = AttributeKey.stringKey("rag.source");
    private static final AttributeKey<Double> KEY_RAG_SCORE    = AttributeKey.doubleKey("rag.chunk.score");
    private static final AttributeKey<String> KEY_RAG_CONTENT  = AttributeKey.stringKey("rag.chunk.content");

    // -- Tool-call event attribute keys --
    private static final AttributeKey<String> KEY_TOOL_NAME    = AttributeKey.stringKey("llm.tool.name");
    private static final AttributeKey<String> KEY_TOOL_CALL_ID = AttributeKey.stringKey("llm.tool.call_id");
    private static final AttributeKey<String> KEY_TOOL_INPUT   = AttributeKey.stringKey("llm.tool.input");
    private static final AttributeKey<String> KEY_TOOL_OUTPUT  = AttributeKey.stringKey("llm.tool.output");

    private SpanHelpers() {}

    /**
     * Sets conversation metadata on the given span.
     *
     * <p>Attributes set:
     * <ul>
     *   <li>{@code llm.conversation.id} — always set when {@code conversationId} is non-null</li>
     *   <li>{@code llm.session.id} — only set when {@code sessionId} is non-null</li>
     *   <li>{@code llm.turn.index} — always set</li>
     * </ul>
     *
     * @param span           the active span to enrich
     * @param conversationId stable ID grouping all turns in a multi-turn conversation
     * @param sessionId      optional session or thread identifier; may be {@code null}
     * @param turnIndex      zero-based position of this turn within the conversation
     */
    public static void recordConversation(Span span, String conversationId,
            String sessionId, int turnIndex) {
        if (conversationId != null) {
            span.setAttribute(KEY_CONV_ID, conversationId);
        }
        if (sessionId != null) {
            span.setAttribute(KEY_SESSION_ID, sessionId);
        }
        span.setAttribute(KEY_TURN_INDEX, (long) turnIndex);
    }

    /**
     * Adds a {@code "rag.chunk.retrieved"} event to the given span.
     *
     * <p>Attributes on the event:
     * <ul>
     *   <li>{@code rag.chunk.id}</li>
     *   <li>{@code rag.source}</li>
     *   <li>{@code rag.chunk.score}</li>
     *   <li>{@code rag.chunk.content} — only added when {@link RagChunk#getContent()} is non-null</li>
     * </ul>
     *
     * @param span  the active span to enrich
     * @param chunk the retrieved chunk; must not be {@code null}
     * @throws NullPointerException if {@code chunk} is null
     */
    public static void recordRagChunk(Span span, RagChunk chunk) {
        if (chunk == null) {
            throw new NullPointerException("SpanHelpers.recordRagChunk: chunk must not be null");
        }

        AttributesBuilder attrs = Attributes.builder()
                .put(KEY_RAG_CHUNK_ID, chunk.getChunkId())
                .put(KEY_RAG_SOURCE,   chunk.getSource())
                .put(KEY_RAG_SCORE,    chunk.getScore());

        if (chunk.getContent() != null) {
            attrs.put(KEY_RAG_CONTENT, chunk.getContent());
        }

        span.addEvent("rag.chunk.retrieved", attrs.build());
    }

    /**
     * Adds a {@code "llm.tool.call"} event to the given span.
     *
     * <p>Attributes on the event:
     * <ul>
     *   <li>{@code llm.tool.name}</li>
     *   <li>{@code llm.tool.call_id}</li>
     *   <li>{@code llm.tool.input} — only added when {@link ToolCall#getInput()} is non-null</li>
     *   <li>{@code llm.tool.output} — only added when {@link ToolCall#getOutput()} is non-null</li>
     * </ul>
     *
     * @param span the active span to enrich
     * @param call the tool call metadata; must not be {@code null}
     * @throws NullPointerException if {@code call} is null
     */
    public static void recordToolCall(Span span, ToolCall call) {
        if (call == null) {
            throw new NullPointerException("SpanHelpers.recordToolCall: call must not be null");
        }

        AttributesBuilder attrs = Attributes.builder()
                .put(KEY_TOOL_NAME,    call.getToolName())
                .put(KEY_TOOL_CALL_ID, call.getCallId());

        if (call.getInput() != null) {
            attrs.put(KEY_TOOL_INPUT, call.getInput());
        }
        if (call.getOutput() != null) {
            attrs.put(KEY_TOOL_OUTPUT, call.getOutput());
        }

        span.addEvent("llm.tool.call", attrs.build());
    }
}
