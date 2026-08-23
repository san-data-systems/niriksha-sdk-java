package ai.niriksha.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fixtures here are the responses the server actually returns.
 *
 * <p>The previous version of this test asserted a shape the API has never produced —
 * a top-level {@code "text"} field on a render response — so it passed while
 * {@code getPrompt} returned an empty prompt for every call. A test written against
 * an imagined contract does not verify anything; it just makes the bug look covered.
 */
class PromptClientTest {

    private final PromptClient client = new PromptClient("https://app.niriksha.ai", "nai_test");

    // ── render ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseRenderResponse reads the rendered text from data.content")
    void parseRenderResponse() {
        // The real envelope: utils.Success wraps the handler's gin.H{"content": …}.
        String json = """
                {
                  "success": true,
                  "request_id": "req-1",
                  "timestamp": "2026-08-23T00:00:00Z",
                  "data": { "content": "Write a description for Widget Pro." }
                }
                """;

        PromptResponse p = client.parseRenderResponse(json, "product-description", 3);

        assertEquals("Write a description for Widget Pro.", p.getText());
        // The response does not echo the name or version, so they are carried
        // through from the call rather than parsed out of nothing.
        assertEquals("product-description", p.getName());
        assertEquals(3, p.getVersion());
    }

    @Test
    @DisplayName("parseRenderResponse defaults the version to 0 when none was pinned")
    void parseRenderResponseWithoutVersion() {
        String json = """
                {"success": true, "data": {"content": "hello"}}
                """;
        assertEquals(0, client.parseRenderResponse(json, "p", null).getVersion());
    }

    @Test
    @DisplayName("parseRenderResponse throws rather than returning an empty prompt")
    void parseRenderResponseRejectsMissingContent() {
        // This is the important one. Silently returning "" is how the old code
        // behaved, and an empty prompt sent to a model is the worst outcome
        // available: nothing errors, and the answer is nonsense.
        String json = """
                {"success": true, "data": {"unexpected": "shape"}}
                """;

        NirikshaAIException e = assertThrows(NirikshaAIException.class,
                () -> client.parseRenderResponse(json, "product-description", null));
        assertTrue(e.getMessage().contains("product-description"),
                "the error should name the prompt that failed to render");
    }

    // ── request body ────────────────────────────────────────────────────────

    @Test
    @DisplayName("buildRenderRequest sends name only when nothing else is set")
    void buildRenderRequestMinimal() {
        assertEquals("{\"name\":\"greeting\"}",
                client.buildRenderRequest("greeting", null, Map.of()));
    }

    @Test
    @DisplayName("buildRenderRequest includes a pinned version")
    void buildRenderRequestWithVersion() {
        assertEquals("{\"name\":\"greeting\",\"version\":5}",
                client.buildRenderRequest("greeting", 5, Map.of()));
    }

    @Test
    @DisplayName("buildRenderRequest sends variables as a JSON object, not query parameters")
    void buildRenderRequestWithVariables() {
        String body = client.buildRenderRequest("greeting", null, Map.of("name", "Widget Pro"));
        assertTrue(body.contains("\"variables\""), body);
        assertTrue(body.contains("\"name\":\"Widget Pro\""), body);
    }

    @Test
    @DisplayName("buildRenderRequest escapes quotes and backslashes")
    void buildRenderRequestEscapes() {
        // An unescaped quote in a variable would produce a malformed body and a
        // 400 that looks like a server problem.
        String body = client.buildRenderRequest("p", null, Map.of("q", "say \"hi\""));
        assertTrue(body.contains("say \\\"hi\\\""), body);
    }

    // ── listing ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parsePromptList reads the fields the listing actually carries")
    void parsePromptList() {
        // GET /api/v1/sdk/prompts returns id, name, description and created_at —
        // no text, no version, no tags.
        String json = """
                {
                  "success": true,
                  "data": [
                    {"id":"1","name":"p1","description":"First","created_at":"2026-01-01T00:00:00Z"},
                    {"id":"2","name":"p2","description":"Second","created_at":"2026-01-02T00:00:00Z"}
                  ],
                  "meta": {"total": 2}
                }
                """;

        List<PromptResponse> list = client.parsePromptList(json);

        assertEquals(2, list.size());
        assertEquals("p1", list.get(0).getName());
        assertEquals("First", list.get(0).getDescription());
        assertEquals("2026-01-02T00:00:00Z", list.get(1).getCreatedAt());
        // Documented, not accidental: a listed entry has no text. Callers render
        // one with getPrompt.
        assertEquals("", list.get(1).getText());
    }

    @Test
    @DisplayName("parsePromptList handles an empty listing")
    void parsePromptListEmpty() {
        assertEquals(0, client.parsePromptList("{\"success\":true,\"data\":[]}").size());
    }

    // ── options + lifecycle ─────────────────────────────────────────────────

    @Test
    @DisplayName("GetPromptOptions builder correctly stores version and variables")
    void getPromptOptions() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .version(5)
                .variable("name", "Widget Pro")
                .variable("price", "$49.99")
                .build();

        assertEquals(5, opts.getVersion());
        assertEquals("Widget Pro", opts.getVariables().get("name"));
        assertEquals("$49.99", opts.getVariables().get("price"));
    }

    @Test
    @DisplayName("NirikshaAI.getPrompt throws IllegalStateException before SDK is initialised")
    void uninitializedGetPromptThrows() {
        try {
            var f1 = NirikshaAI.class.getDeclaredField("_promptClient");
            f1.setAccessible(true);
            f1.set(null, null);
        } catch (Exception e) {
            // if reflection blocked, skip this specific test
            return;
        }
        assertThrows(IllegalStateException.class, () -> NirikshaAI.getPrompt("test-prompt"));
    }
}
