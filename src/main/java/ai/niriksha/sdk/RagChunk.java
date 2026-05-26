package ai.niriksha.sdk;

/**
 * Metadata for a single retrieved document chunk.
 *
 * <p>Use {@link #builder()} to construct instances:
 * <pre>{@code
 * RagChunk chunk = RagChunk.builder()
 *     .chunkId("chunk-42")
 *     .source("s3://docs/manual.pdf#page=7")
 *     .score(0.87)
 *     .content("The warranty period is 12 months from purchase.")
 *     .build();
 * SpanHelpers.recordRagChunk(span, chunk);
 * }</pre>
 */
public final class RagChunk {

    private final String chunkId;
    private final String source;
    private final double score;
    private final String content; // may be null

    private RagChunk(Builder b) {
        this.chunkId = b.chunkId;
        this.source  = b.source;
        this.score   = b.score;
        this.content = b.content;
    }

    /** Unique identifier for the retrieved chunk. */
    public String getChunkId() {
        return chunkId;
    }

    /** Origin of the chunk (e.g. a document URI, table name, or file path). */
    public String getSource() {
        return source;
    }

    /** Retrieval relevance score (higher is more relevant). */
    public double getScore() {
        return score;
    }

    /**
     * Raw text content of the chunk, or {@code null} if not captured.
     * Avoid recording content that may contain PII; use {@link PiiRedactor} first.
     */
    public String getContent() {
        return content;
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link RagChunk}. */
    public static final class Builder {

        private String chunkId;
        private String source;
        private double score;
        private String content;

        private Builder() {}

        /** Sets the unique chunk identifier. Required. */
        public Builder chunkId(String chunkId) {
            this.chunkId = chunkId;
            return this;
        }

        /** Sets the origin of the chunk. Required. */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /** Sets the retrieval relevance score. */
        public Builder score(double score) {
            this.score = score;
            return this;
        }

        /** Sets the raw text content of the chunk. Optional. */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * Builds the {@link RagChunk}.
         *
         * @throws IllegalStateException if {@code chunkId} or {@code source} is blank or null
         */
        public RagChunk build() {
            if (chunkId == null || chunkId.isBlank()) {
                throw new IllegalStateException("RagChunk: chunkId is required");
            }
            if (source == null || source.isBlank()) {
                throw new IllegalStateException("RagChunk: source is required");
            }
            return new RagChunk(this);
        }
    }
}
