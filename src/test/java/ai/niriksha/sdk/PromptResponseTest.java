package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptResponseTest {

    @Test
    @DisplayName("getName() returns the name passed to the constructor")
    void getName() {
        PromptResponse response = new PromptResponse(
                "product-description", 3, "Write a description.", "Product copy", null, null, null);

        assertEquals("product-description", response.getName());
    }

    @Test
    @DisplayName("getVersion() returns the version passed to the constructor")
    void getVersion() {
        PromptResponse response = new PromptResponse(
                "my-prompt", 5, "Hello {{name}}.", null, null, null, null);

        assertEquals(5, response.getVersion());
    }

    @Test
    @DisplayName("getText() returns the rendered prompt text")
    void getText() {
        PromptResponse response = new PromptResponse(
                "greeting", 1, "Hello, world!", null, null, null, null);

        assertEquals("Hello, world!", response.getText());
    }

    @Test
    @DisplayName("getDescription() returns the human-readable description")
    void getDescription() {
        PromptResponse response = new PromptResponse(
                "summary-prompt", 2, "Summarise the text.", "Summarisation helper",
                null, null, null);

        assertEquals("Summarisation helper", response.getDescription());
    }

    @Test
    @DisplayName("getTags() returns the provided tags list as an immutable copy")
    void getTags() {
        List<String> tags = List.of("nlp", "summary", "production");
        PromptResponse response = new PromptResponse(
                "tag-prompt", 1, "text", "desc", tags, null, null);

        assertEquals(3, response.getTags().size());
        assertTrue(response.getTags().containsAll(tags));
    }

    @Test
    @DisplayName("getTags() returns an empty list when null tags are passed to the constructor")
    void nullTagsDefaultToEmptyList() {
        PromptResponse response = new PromptResponse(
                "no-tags-prompt", 1, "text", "desc", null, null, null);

        assertNotNull(response.getTags());
        assertTrue(response.getTags().isEmpty());
    }

    @Test
    @DisplayName("getCreatedAt() returns the ISO-8601 timestamp when provided")
    void getCreatedAt() {
        PromptResponse response = new PromptResponse(
                "ts-prompt", 1, "text", "desc", null, "2024-01-15T10:30:00Z", null);

        assertEquals("2024-01-15T10:30:00Z", response.getCreatedAt());
    }

    @Test
    @DisplayName("getCreatedAt() returns empty string when null is passed")
    void nullCreatedAtDefaultsToEmptyString() {
        PromptResponse response = new PromptResponse(
                "ts-prompt", 1, "text", "desc", null, null, null);

        assertEquals("", response.getCreatedAt());
    }

    @Test
    @DisplayName("getUpdatedAt() returns the ISO-8601 timestamp when provided")
    void getUpdatedAt() {
        PromptResponse response = new PromptResponse(
                "ts-prompt", 1, "text", "desc", null, null, "2024-06-20T08:00:00Z");

        assertEquals("2024-06-20T08:00:00Z", response.getUpdatedAt());
    }

    @Test
    @DisplayName("getUpdatedAt() returns empty string when null is passed")
    void nullUpdatedAtDefaultsToEmptyString() {
        PromptResponse response = new PromptResponse(
                "ts-prompt", 1, "text", "desc", null, null, null);

        assertEquals("", response.getUpdatedAt());
    }

    @Test
    @DisplayName("getTags() returns an immutable list — mutation throws UnsupportedOperationException")
    void tagsListIsImmutable() {
        PromptResponse response = new PromptResponse(
                "immutable-tags", 1, "text", "desc",
                List.of("alpha", "beta"), null, null);

        assertThrows(UnsupportedOperationException.class,
                () -> response.getTags().add("gamma"),
                "Tags list must be immutable");
    }

    @Test
    @DisplayName("toString() contains the name and version")
    void toStringContainsNameAndVersion() {
        PromptResponse response = new PromptResponse(
                "debug-prompt", 42, "text", "desc", null, null, null);

        String str = response.toString();
        assertTrue(str.contains("debug-prompt"), "toString must include the name");
        assertTrue(str.contains("42"), "toString must include the version");
    }
}
