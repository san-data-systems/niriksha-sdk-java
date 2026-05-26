package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GetPromptOptionsTest {

    @Test
    @DisplayName("builder with version stores the version and getVersion returns it")
    void builderWithVersion() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .version(3)
                .build();

        assertEquals(3, opts.getVersion());
    }

    @Test
    @DisplayName("version is null when not set — defaults to latest")
    void versionDefaultsToNull() {
        GetPromptOptions opts = GetPromptOptions.builder().build();

        assertNull(opts.getVersion());
    }

    @Test
    @DisplayName("single variable added via variable() is retrievable from getVariables()")
    void singleVariable() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .variable("product_name", "Widget Pro")
                .build();

        assertEquals("Widget Pro", opts.getVariables().get("product_name"));
        assertEquals(1, opts.getVariables().size());
    }

    @Test
    @DisplayName("multiple variables added via variable() are all stored")
    void multipleVariables() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .variable("product_name", "Widget Pro")
                .variable("category", "Electronics")
                .variable("price", "$49.99")
                .build();

        Map<String, String> vars = opts.getVariables();
        assertEquals("Widget Pro", vars.get("product_name"));
        assertEquals("Electronics", vars.get("category"));
        assertEquals("$49.99", vars.get("price"));
        assertEquals(3, vars.size());
    }

    @Test
    @DisplayName("variables(Map) bulk-loads all entries into the options")
    void bulkVariables() {
        Map<String, String> input = Map.of("k1", "v1", "k2", "v2");
        GetPromptOptions opts = GetPromptOptions.builder()
                .variables(input)
                .build();

        assertEquals("v1", opts.getVariables().get("k1"));
        assertEquals("v2", opts.getVariables().get("k2"));
    }

    @Test
    @DisplayName("getVariables() returns an unmodifiable map — mutation throws UnsupportedOperationException")
    void variablesMapIsUnmodifiable() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .variable("key", "value")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> opts.getVariables().put("another", "entry"),
                "Returned map must not be mutable");
    }

    @Test
    @DisplayName("getVariables() returns empty map when no variables are set")
    void emptyVariablesMap() {
        GetPromptOptions opts = GetPromptOptions.builder().build();

        assertNotNull(opts.getVariables());
        assertTrue(opts.getVariables().isEmpty());
    }

    @Test
    @DisplayName("version and variables can be combined on the same builder")
    void versionAndVariablesCombined() {
        GetPromptOptions opts = GetPromptOptions.builder()
                .version(7)
                .variable("tone", "formal")
                .build();

        assertEquals(7, opts.getVersion());
        assertEquals("formal", opts.getVariables().get("tone"));
    }
}
