package ai.niriksha.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ObserveTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @BeforeEach
    void useTestOtel() {
        Observe.using(otelTesting.getOpenTelemetry());
    }

    private Map<String, SpanData> byName() {
        return otelTesting.getSpans().stream().collect(Collectors.toMap(SpanData::getName, s -> s));
    }

    @Test
    void observeMakesARunWithTypedChildrenAndALogEvent() throws Exception {
        String out = Observe.observe("research-agent", () -> {
            Observe.log("info", "started", Map.of("q", "x"));
            String plan = Observe.span("plan", Observe.SpanType.LLM, () -> "p", Map.of("gen_ai.request.model", "gpt-4o"));
            List<String> docs = Observe.span("search", Observe.SpanType.TOOL, () -> List.of("d"));
            return plan + ":" + docs.size();
        });
        assertEquals("p:1", out);

        Map<String, SpanData> spans = byName();
        SpanData root = spans.get("research-agent");
        assertNotNull(root);
        assertFalse(root.getParentSpanContext().isValid());
        assertEquals(Boolean.TRUE, root.getAttributes().get(Observe.RUN_ATTR));
        assertEquals("invoke_agent", root.getAttributes().get(Observe.OPERATION));
        assertEquals("research-agent", root.getAttributes().get(Observe.AGENT_NAME));
        assertEquals(StatusCode.OK, root.getStatus().getStatusCode());
        assertEquals(1, root.getEvents().size());
        assertEquals("started", root.getEvents().get(0).getName());

        SpanData plan = spans.get("plan");
        assertEquals(root.getSpanId(), plan.getParentSpanId());
        assertEquals("chat", plan.getAttributes().get(Observe.OPERATION));
        assertEquals("gpt-4o", plan.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
        SpanData search = spans.get("search");
        assertEquals("execute_tool", search.getAttributes().get(Observe.OPERATION));
        assertEquals("search", search.getAttributes().get(Observe.TOOL_NAME));
    }

    @Test
    void errorsAreRecordedAndRethrown() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> Observe.observe("failing", () -> { throw new IllegalStateException("boom"); }));
        assertEquals("boom", thrown.getMessage());
        SpanData failing = byName().get("failing");
        assertEquals(StatusCode.ERROR, failing.getStatus().getStatusCode());
        assertTrue(failing.getEvents().stream().anyMatch(e -> e.getName().equals("exception")));
    }

    @Test
    void attributesAreCoerced() throws Exception {
        Observe.observe("a", () -> null, Map.of("n", 3, "ok", true, "tags", List.of("x", "y"), "obj", Map.of("k", 1)));
        SpanData root = byName().get("a");
        assertEquals(3L, root.getAttributes().get(AttributeKey.longKey("n")));
        assertEquals(Boolean.TRUE, root.getAttributes().get(AttributeKey.booleanKey("ok")));
        assertEquals(List.of("x", "y"), root.getAttributes().get(AttributeKey.stringArrayKey("tags")));
        assertEquals("{k=1}", root.getAttributes().get(AttributeKey.stringKey("obj")));
    }
}
