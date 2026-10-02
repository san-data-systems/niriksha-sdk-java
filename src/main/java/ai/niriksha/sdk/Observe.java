package ai.niriksha.sdk;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Wrap an agent so the platform sees it as a run.
 *
 * <ul>
 *   <li>{@link #observe(String, Callable)} wraps an agent entry point in a root span. The
 *       platform turns it into an <b>agent run</b>: it appears on LLM → Runs as soon as the first
 *       child span arrives, flips to Succeeded or Failed when this span ends, and the health
 *       rules (long running, repeated tool, repeated error, no activity, possible loop)
 *       evaluate it.</li>
 *   <li>{@link #span(String, SpanType, Callable)} is a unit of work inside the run — an LLM
 *       call, a tool call, a retrieval or a nested agent.</li>
 *   <li>{@link #log(String, String, Map)} is a structured line attached to the active run.</li>
 * </ul>
 *
 * Plain OpenTelemetry with the GenAI semantic conventions ({@code gen_ai.operation.name},
 * {@code gen_ai.agent.name}, {@code gen_ai.tool.name}); no LLM library is required.
 *
 * <pre>{@code
 * String answer = Observe.observe("research-agent", () -> {
 *     Observe.log("info", "agent started", Map.of("question", q));
 *     String plan = Observe.span("plan", SpanType.LLM, () -> callModel(q), Map.of("gen_ai.request.model", "gpt-4o"));
 *     List<String> docs = Observe.span("search", SpanType.TOOL, () -> search(plan));
 *     return Observe.span("answer", SpanType.LLM, () -> callModel(docs));
 * });
 * }</pre>
 */
public final class Observe {

    /** The kind of work a step is; maps onto {@code gen_ai.operation.name}. */
    public enum SpanType {
        LLM("chat"), TOOL("execute_tool"), AGENT("invoke_agent"), RETRIEVAL("retrieval");

        private final String operation;

        SpanType(String operation) {
            this.operation = operation;
        }

        String operation() {
            return operation;
        }
    }

    static final String TRACER_NAME = "nirikshaai.observe";
    /** Marks the root span of a run so the platform never guesses from a missing parent alone. */
    static final AttributeKey<Boolean> RUN_ATTR = AttributeKey.booleanKey("niriksha.run");
    static final AttributeKey<String> OPERATION = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> LOG_LEVEL = AttributeKey.stringKey("log.level");

    private static volatile OpenTelemetry openTelemetry;

    private Observe() {
    }

    /**
     * Use a specific {@link OpenTelemetry} instance instead of the global one — tests, or
     * applications that keep more than one.
     */
    public static void using(OpenTelemetry otel) {
        openTelemetry = otel;
    }

    private static OpenTelemetry otel() {
        OpenTelemetry o = openTelemetry;
        return o != null ? o : GlobalOpenTelemetry.get();
    }

    private static Tracer tracer() {
        return otel().getTracer(TRACER_NAME);
    }

    /** Wrap an agent entry point in a root span — the platform's run. */
    public static <T> T observe(String name, Callable<T> body) throws Exception {
        return observe(name, body, Map.of());
    }

    /** {@link #observe(String, Callable)} with extra attributes on the root span. */
    public static <T> T observe(String name, Callable<T> body, Map<String, ?> attributes) throws Exception {
        AttributesBuilder b = Attributes.builder()
                .put(RUN_ATTR, true)
                .put(OPERATION, SpanType.AGENT.operation())
                .put(AGENT_NAME, name);
        putAll(b, attributes);
        return run(name, b.build(), body);
    }

    /** A unit of work inside the run; exceptions are recorded on the span and re-thrown. */
    public static <T> T span(String name, SpanType type, Callable<T> body) throws Exception {
        return span(name, type, body, Map.of());
    }

    /** {@link #span(String, SpanType, Callable)} with extra attributes (e.g. {@code gen_ai.request.model}). */
    public static <T> T span(String name, SpanType type, Callable<T> body, Map<String, ?> attributes) throws Exception {
        AttributesBuilder b = Attributes.builder().put(OPERATION, type.operation());
        if (type == SpanType.TOOL) {
            b.put(TOOL_NAME, name);
        } else if (type == SpanType.AGENT) {
            b.put(AGENT_NAME, name);
        }
        putAll(b, attributes);
        return run(name, b.build(), body);
    }

    /** Side-effect-only form of {@link #span(String, SpanType, Callable)}. */
    public static void span(String name, SpanType type, Runnable body) throws Exception {
        span(name, type, () -> {
            body.run();
            return null;
        });
    }

    /**
     * A structured log line attached to the active run: an event on the current span (so the run
     * page shows it even with log export off) and an OTel log record with the trace context.
     * {@code level} is trace | debug | info | warn | error | fatal.
     */
    public static void log(String level, String message, Map<String, ?> attributes) {
        AttributesBuilder b = Attributes.builder();
        putAll(b, attributes);
        Attributes attrs = b.build();
        Span current = Span.current();
        if (current.isRecording()) {
            current.addEvent(message, attrs.toBuilder().put(LOG_LEVEL, level.toLowerCase(Locale.ROOT)).build());
        }
        otel().getLogsBridge().get("nirikshaai.agent").logRecordBuilder()
                .setBody(message)
                .setSeverity(severityFor(level))
                .setSeverityText(level.toUpperCase(Locale.ROOT))
                .setAllAttributes(attrs)
                .emit();
    }

    private static <T> T run(String name, Attributes attributes, Callable<T> body) throws Exception {
        Span sp = tracer().spanBuilder(name).setSpanKind(SpanKind.INTERNAL).setAllAttributes(attributes).startSpan();
        try (Scope ignored = sp.makeCurrent()) {
            T result = body.call();
            sp.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            sp.recordException(e);
            sp.setStatus(StatusCode.ERROR, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            throw e;
        } finally {
            sp.end();
        }
    }

    /** Primitives and string lists pass through; anything else is {@code toString()}'d; nulls are dropped. */
    static void putAll(AttributesBuilder b, Map<String, ?> attributes) {
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, ?> e : attributes.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            String k = e.getKey();
            if (v instanceof String) {
                b.put(AttributeKey.stringKey(k), (String) v);
            } else if (v instanceof Boolean) {
                b.put(AttributeKey.booleanKey(k), (Boolean) v);
            } else if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
                b.put(AttributeKey.longKey(k), ((Number) v).longValue());
            } else if (v instanceof Double || v instanceof Float) {
                b.put(AttributeKey.doubleKey(k), ((Number) v).doubleValue());
            } else if (v instanceof List && ((List<?>) v).stream().allMatch(x -> x instanceof String)) {
                @SuppressWarnings("unchecked")
                List<String> strings = (List<String>) v;
                b.put(AttributeKey.stringArrayKey(k), strings);
            } else {
                b.put(AttributeKey.stringKey(k), String.valueOf(v));
            }
        }
    }

    static Severity severityFor(String level) {
        switch (level.toLowerCase(Locale.ROOT)) {
            case "trace":
                return Severity.TRACE;
            case "debug":
                return Severity.DEBUG;
            case "warn":
            case "warning":
                return Severity.WARN;
            case "error":
                return Severity.ERROR;
            case "fatal":
                return Severity.FATAL;
            default:
                return Severity.INFO;
        }
    }
}
