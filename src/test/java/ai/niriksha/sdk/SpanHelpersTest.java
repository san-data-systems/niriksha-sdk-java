package ai.niriksha.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpanHelpersTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private Tracer tracer() {
        return otelTesting.getOpenTelemetry().getTracer("test-tracer");
    }

    // -----------------------------------------------------------------------
    // recordConversation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("recordConversation sets llm.conversation.id, llm.session.id, and llm.turn.index on the span")
    void recordConversation_setsAllAttributes() {
        Span span = tracer().spanBuilder("test.chat").startSpan();
        SpanHelpers.recordConversation(span, "conv-001", "session-xyz", 2);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        assertEquals("conv-001",
                data.getAttributes().get(AttributeKey.stringKey("llm.conversation.id")));
        assertEquals("session-xyz",
                data.getAttributes().get(AttributeKey.stringKey("llm.session.id")));
        assertEquals(2L,
                data.getAttributes().get(AttributeKey.longKey("llm.turn.index")));
    }

    @Test
    @DisplayName("recordConversation omits llm.conversation.id when conversationId is null")
    void recordConversation_nullConversationIdOmitted() {
        Span span = tracer().spanBuilder("test.chat.no-conv").startSpan();
        SpanHelpers.recordConversation(span, null, "session-abc", 0);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        assertNull(data.getAttributes().get(AttributeKey.stringKey("llm.conversation.id")),
                "Attribute must not be set when conversationId is null");
        assertEquals(0L,
                data.getAttributes().get(AttributeKey.longKey("llm.turn.index")));
    }

    @Test
    @DisplayName("recordConversation omits llm.session.id when sessionId is null")
    void recordConversation_nullSessionIdOmitted() {
        Span span = tracer().spanBuilder("test.chat.no-session").startSpan();
        SpanHelpers.recordConversation(span, "conv-002", null, 1);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        assertNull(data.getAttributes().get(AttributeKey.stringKey("llm.session.id")),
                "Attribute must not be set when sessionId is null");
        assertEquals("conv-002",
                data.getAttributes().get(AttributeKey.stringKey("llm.conversation.id")));
    }

    @Test
    @DisplayName("recordConversation sets llm.turn.index even when both string params are null")
    void recordConversation_turnIndexAlwaysSet() {
        Span span = tracer().spanBuilder("test.chat.nulls").startSpan();
        SpanHelpers.recordConversation(span, null, null, 5);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        assertEquals(5L,
                data.getAttributes().get(AttributeKey.longKey("llm.turn.index")));
    }

    // -----------------------------------------------------------------------
    // recordRagChunk
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("recordRagChunk adds rag.chunk.retrieved event with correct attributes")
    void recordRagChunk_addsEventWithAllAttributes() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-7")
                .source("s3://docs/faq.pdf#page=3")
                .score(0.91)
                .content("Refunds are processed within 5 business days.")
                .build();

        Span span = tracer().spanBuilder("test.rag").startSpan();
        SpanHelpers.recordRagChunk(span, chunk);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        List<EventData> events = data.getEvents();
        assertEquals(1, events.size());

        EventData event = events.get(0);
        assertEquals("rag.chunk.retrieved", event.getName());
        assertEquals("chunk-7", event.getAttributes().get(AttributeKey.stringKey("rag.chunk.id")));
        assertEquals("s3://docs/faq.pdf#page=3", event.getAttributes().get(AttributeKey.stringKey("rag.source")));
        assertEquals(0.91, event.getAttributes().get(AttributeKey.doubleKey("rag.chunk.score")), 1e-9);
        assertEquals("Refunds are processed within 5 business days.",
                event.getAttributes().get(AttributeKey.stringKey("rag.chunk.content")));
    }

    @Test
    @DisplayName("recordRagChunk omits rag.chunk.content attribute when chunk content is null")
    void recordRagChunk_omitsContentWhenNull() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-8")
                .source("db://knowledge")
                .score(0.75)
                .build();

        Span span = tracer().spanBuilder("test.rag.no-content").startSpan();
        SpanHelpers.recordRagChunk(span, chunk);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        EventData event = data.getEvents().get(0);
        assertNull(event.getAttributes().get(AttributeKey.stringKey("rag.chunk.content")),
                "rag.chunk.content must not be present when chunk content is null");
    }

    @Test
    @DisplayName("recordRagChunk throws NullPointerException when chunk is null")
    void recordRagChunk_throwsOnNullChunk() {
        Span span = tracer().spanBuilder("test.rag.null").startSpan();
        try {
            assertThrows(NullPointerException.class,
                    () -> SpanHelpers.recordRagChunk(span, null));
        } finally {
            span.end();
        }
    }

    // -----------------------------------------------------------------------
    // recordToolCall
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("recordToolCall adds llm.tool.call event with all attributes")
    void recordToolCall_addsEventWithAllAttributes() {
        ToolCall call = ToolCall.builder()
                .toolName("search_logs")
                .callId("call_abc123")
                .input("{\"query\":\"error rate\"}")
                .output("{\"count\":42}")
                .build();

        Span span = tracer().spanBuilder("test.tool").startSpan();
        SpanHelpers.recordToolCall(span, call);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        List<EventData> events = data.getEvents();
        assertEquals(1, events.size());

        EventData event = events.get(0);
        assertEquals("llm.tool.call", event.getName());
        assertEquals("search_logs", event.getAttributes().get(AttributeKey.stringKey("llm.tool.name")));
        assertEquals("call_abc123", event.getAttributes().get(AttributeKey.stringKey("llm.tool.call_id")));
        assertEquals("{\"query\":\"error rate\"}",
                event.getAttributes().get(AttributeKey.stringKey("llm.tool.input")));
        assertEquals("{\"count\":42}",
                event.getAttributes().get(AttributeKey.stringKey("llm.tool.output")));
    }

    @Test
    @DisplayName("recordToolCall omits llm.tool.input attribute when input is null")
    void recordToolCall_omitsInputWhenNull() {
        ToolCall call = ToolCall.builder()
                .toolName("noop_tool")
                .callId("call_noop")
                .build();

        Span span = tracer().spanBuilder("test.tool.no-input").startSpan();
        SpanHelpers.recordToolCall(span, call);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        EventData event = data.getEvents().get(0);
        assertNull(event.getAttributes().get(AttributeKey.stringKey("llm.tool.input")),
                "llm.tool.input must not be present when input is null");
    }

    @Test
    @DisplayName("recordToolCall omits llm.tool.output attribute when output is null")
    void recordToolCall_omitsOutputWhenNull() {
        ToolCall call = ToolCall.builder()
                .toolName("fire_and_forget")
                .callId("call_fire")
                .input("{\"target\":\"queue\"}")
                .build();

        Span span = tracer().spanBuilder("test.tool.no-output").startSpan();
        SpanHelpers.recordToolCall(span, call);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        EventData event = data.getEvents().get(0);
        assertNull(event.getAttributes().get(AttributeKey.stringKey("llm.tool.output")),
                "llm.tool.output must not be present when output is null");
    }

    @Test
    @DisplayName("recordToolCall throws NullPointerException when call is null")
    void recordToolCall_throwsOnNullCall() {
        Span span = tracer().spanBuilder("test.tool.null").startSpan();
        try {
            assertThrows(NullPointerException.class,
                    () -> SpanHelpers.recordToolCall(span, null));
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("multiple helper calls on one span accumulate independent events")
    void multipleEventsAccumulate() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-multi")
                .source("wiki://article")
                .score(0.8)
                .build();

        ToolCall call = ToolCall.builder()
                .toolName("summarise")
                .callId("call_sum")
                .build();

        Span span = tracer().spanBuilder("test.multi").startSpan();
        SpanHelpers.recordConversation(span, "conv-multi", null, 0);
        SpanHelpers.recordRagChunk(span, chunk);
        SpanHelpers.recordToolCall(span, call);
        span.end();

        SpanData data = otelTesting.getSpans().get(0);
        assertEquals(2, data.getEvents().size(),
                "Span must have exactly two events: rag.chunk.retrieved and llm.tool.call");

        List<String> eventNames = data.getEvents().stream()
                .map(EventData::getName)
                .toList();
        assertTrue(eventNames.contains("rag.chunk.retrieved"));
        assertTrue(eventNames.contains("llm.tool.call"));
    }
}
