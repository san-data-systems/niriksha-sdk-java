package ai.niriksha.sdk;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Input for a single LLM evaluation result.
 *
 * <p>Use {@link #builder()} to construct instances:
 * <pre>{@code
 * EvalInput eval = EvalInput.builder()
 *     .traceId(span.getSpanContext().getTraceId())
 *     .metricName("faithfulness")
 *     .score(0.92)
 *     .label("pass")
 *     .explanation("Response accurately reflects the source documents")
 *     .evalType("llm_judge")
 *     .build();
 * NirikshaAI.submitEval(eval);
 * }</pre>
 */
public final class EvalInput {

    private final String traceId;
    private final String metricName;
    private final double score;
    private final String label;
    private final String explanation;
    private final String evalType;

    /** Groups evals into an A/B experiment. Optional. */
    private final String experimentId;

    /** Evaluator confidence in [0, 1]. {@code null} means unset. */
    private final Double confidence;

    /** Arbitrary context key-value pairs. Never {@code null}; may be empty. */
    private final Map<String, String> metadata;

    /** When the evaluation was performed. {@code null} means server time. */
    private final Instant evalTime;

    private EvalInput(Builder b) {
        this.traceId      = b.traceId;
        this.metricName   = b.metricName;
        this.score        = b.score;
        this.label        = b.label;
        this.explanation  = b.explanation;
        this.evalType     = b.evalType;
        this.experimentId = b.experimentId;
        this.confidence   = b.confidence;
        this.metadata     = Collections.unmodifiableMap(new HashMap<>(b.metadata));
        this.evalTime     = b.evalTime;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMetricName() {
        return metricName;
    }

    public double getScore() {
        return score;
    }

    public String getLabel() {
        return label;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getEvalType() {
        return evalType;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getEvalTime() {
        return evalTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String traceId;
        private String metricName;
        private double score;
        private String label = "pass";
        private String explanation;
        private String evalType = "llm_judge";
        private String experimentId;
        private Double confidence;
        private final Map<String, String> metadata = new HashMap<>();
        private Instant evalTime;

        private Builder() {}

        /** 32-character hex OpenTelemetry trace ID. */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /** Name of the evaluation metric (e.g. "faithfulness", "toxicity"). */
        public Builder metricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        /** Score in the range [0, 1]. */
        public Builder score(double score) {
            this.score = score;
            return this;
        }

        /** Human-readable outcome label (e.g. "pass", "fail"). */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /** Optional free-text explanation of the score. */
        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        /** Evaluation method: "llm_judge", "rule_based", or "human". Default: "llm_judge". */
        public Builder evalType(String evalType) {
            this.evalType = evalType;
            return this;
        }

        /** Groups this eval into a named A/B experiment. Optional. */
        public Builder experimentId(String experimentId) {
            this.experimentId = experimentId;
            return this;
        }

        /** Evaluator confidence score in [0, 1]. Optional. */
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        /** Arbitrary context metadata. Optional. */
        public Builder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /** When the evaluation was performed. {@code null} defers to server time. */
        public Builder evalTime(Instant evalTime) {
            this.evalTime = evalTime;
            return this;
        }

        public EvalInput build() {
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalStateException("EvalInput: traceId is required");
            }
            if (metricName == null || metricName.isBlank()) {
                throw new IllegalStateException("EvalInput: metricName is required");
            }
            if (score < 0 || score > 1) {
                throw new IllegalStateException("EvalInput: score must be in [0, 1]");
            }
            return new EvalInput(this);
        }
    }
}
