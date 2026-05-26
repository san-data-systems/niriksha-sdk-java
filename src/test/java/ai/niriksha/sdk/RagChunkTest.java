package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagChunkTest {

    @Test
    @DisplayName("builder sets all fields and getters return correct values")
    void builderSetsAllFields() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-42")
                .source("s3://docs/manual.pdf#page=7")
                .score(0.87)
                .content("The warranty period is 12 months from purchase.")
                .build();

        assertEquals("chunk-42", chunk.getChunkId());
        assertEquals("s3://docs/manual.pdf#page=7", chunk.getSource());
        assertEquals(0.87, chunk.getScore(), 1e-9);
        assertEquals("The warranty period is 12 months from purchase.", chunk.getContent());
    }

    @Test
    @DisplayName("content is optional and defaults to null when not set")
    void contentIsOptional() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-1")
                .source("db://knowledge/table")
                .score(0.95)
                .build();

        assertNull(chunk.getContent());
    }

    @Test
    @DisplayName("score defaults to 0.0 when not explicitly set")
    void scoreDefaultsToZero() {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-2")
                .source("file://local/doc.txt")
                .build();

        assertEquals(0.0, chunk.getScore(), 1e-9);
    }

    @Test
    @DisplayName("build() throws IllegalStateException when chunkId is null")
    void nullChunkIdThrows() {
        assertThrows(IllegalStateException.class, () ->
                RagChunk.builder()
                        .source("s3://bucket/file.pdf")
                        .score(0.5)
                        .build(),
                "build() should throw when chunkId is null");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when chunkId is blank")
    void blankChunkIdThrows() {
        assertThrows(IllegalStateException.class, () ->
                RagChunk.builder()
                        .chunkId("   ")
                        .source("s3://bucket/file.pdf")
                        .score(0.5)
                        .build(),
                "build() should throw when chunkId is blank");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when source is null")
    void nullSourceThrows() {
        assertThrows(IllegalStateException.class, () ->
                RagChunk.builder()
                        .chunkId("chunk-3")
                        .score(0.5)
                        .build(),
                "build() should throw when source is null");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when source is blank")
    void blankSourceThrows() {
        assertThrows(IllegalStateException.class, () ->
                RagChunk.builder()
                        .chunkId("chunk-4")
                        .source("")
                        .score(0.5)
                        .build(),
                "build() should throw when source is empty string");
    }

    @Test
    @DisplayName("builder produces immutable instances — two builds from the same builder are independent")
    void builderProducesIndependentInstances() {
        RagChunk.Builder builder = RagChunk.builder()
                .chunkId("shared-id")
                .source("shared-source")
                .score(0.5);

        RagChunk first = builder.build();
        RagChunk second = builder.score(0.9).build();

        assertNotSame(first, second);
        assertEquals(0.5, first.getScore(), 1e-9);
        assertEquals(0.9, second.getScore(), 1e-9);
    }
}
