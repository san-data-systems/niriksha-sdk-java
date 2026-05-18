package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptClientTest {

    private final PromptClient client = new PromptClient("https://app.niriksha.ai", "nai_test");

    @Test
    @DisplayName("parsePromptResponse extracts all fields from a JSON object")
    void parsePromptResponse() {
        String json = """
                {
                  "name": "product-description",
                  "version": 3,
                  "text": "Write a description for {{product_name}}.",
                  "description": "Product copy generator"
                }
                """;

        PromptResponse p = client.parsePromptResponse(json);

        assertEquals("product-description", p.getName());
        assertEquals(3, p.getVersion());
        assertEquals("Write a description for {{product_name}}.", p.getText());
        assertEquals("Product copy generator", p.getDescription());
    }

    @Test
    @DisplayName("parsePromptList extracts multiple prompt objects from a JSON array wrapper")
    void parsePromptList() {
        String json = """
                {"prompts": [
                  {"name":"p1","version":1,"text":"Hello","description":"First"},
                  {"name":"p2","version":2,"text":"World","description":"Second"}
                ]}
                """;

        List<PromptResponse> list = client.parsePromptList(json);

        assertEquals(2, list.size());
        assertEquals("p1", list.get(0).getName());
        assertEquals("p2", list.get(1).getName());
        assertEquals(2, list.get(1).getVersion());
    }

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
        // Reset eval/prompt clients
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
