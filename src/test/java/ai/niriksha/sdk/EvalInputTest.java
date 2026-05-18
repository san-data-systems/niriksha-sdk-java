package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvalInputTest {

    @Test
    @DisplayName("Builder produces correct field values")
    void builderFields() {
        EvalInput e = EvalInput.builder()
                .traceId("abc123def456abc123def456abc12345")
                .metricName("faithfulness")
                .score(0.92)
                .label("pass")
                .explanation("Response is accurate")
                .evalType("llm_judge")
                .build();

        assertEquals("abc123def456abc123def456abc12345", e.getTraceId());
        assertEquals("faithfulness", e.getMetricName());
        assertEquals(0.92, e.getScore(), 1e-9);
        assertEquals("pass", e.getLabel());
        assertEquals("Response is accurate", e.getExplanation());
        assertEquals("llm_judge", e.getEvalType());
    }

    @Test
    @DisplayName("Default label is 'pass' and default evalType is 'llm_judge'")
    void defaults() {
        EvalInput e = EvalInput.builder()
                .traceId("abc123def456abc123def456abc12345")
                .metricName("toxicity")
                .score(0.01)
                .build();

        assertEquals("pass", e.getLabel());
        assertEquals("llm_judge", e.getEvalType());
        assertNull(e.getExplanation());
    }

    @Test
    @DisplayName("build() throws when traceId is missing")
    void missingTraceId() {
        assertThrows(IllegalStateException.class, () ->
                EvalInput.builder().metricName("relevance").score(0.8).build());
    }

    @Test
    @DisplayName("build() throws when score is out of range")
    void scoreOutOfRange() {
        assertThrows(IllegalStateException.class, () ->
                EvalInput.builder()
                        .traceId("abc123def456abc123def456abc12345")
                        .metricName("relevance")
                        .score(1.5)
                        .build());
    }

    @Test
    @DisplayName("EvalInput builder accepts new optional fields")
    void newOptionalFields() {
        EvalInput e = EvalInput.builder()
                .traceId("abc123def456abc123def456abc12345")
                .metricName("faithfulness")
                .score(0.9)
                .experimentId("exp-001")
                .confidence(0.85)
                .metadata(java.util.Map.of("model", "gpt-4o"))
                .evalTime(java.time.Instant.now())
                .build();
        assertEquals("exp-001", e.getExperimentId());
        assertEquals(0.85, e.getConfidence(), 1e-9);
        assertEquals("gpt-4o", e.getMetadata().get("model"));
        assertNotNull(e.getEvalTime());
    }
}
