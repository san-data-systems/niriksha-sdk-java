package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    @DisplayName("builder sets all fields and getters return correct values")
    void builderSetsAllFields() {
        ToolCall call = ToolCall.builder()
                .toolName("search_logs")
                .callId("call_abc123")
                .input("{\"query\":\"error rate\",\"window\":\"5m\"}")
                .output("{\"count\":42}")
                .build();

        assertEquals("search_logs", call.getToolName());
        assertEquals("call_abc123", call.getCallId());
        assertEquals("{\"query\":\"error rate\",\"window\":\"5m\"}", call.getInput());
        assertEquals("{\"count\":42}", call.getOutput());
    }

    @Test
    @DisplayName("input is optional and defaults to null when not set")
    void inputIsOptional() {
        ToolCall call = ToolCall.builder()
                .toolName("get_weather")
                .callId("call_xyz789")
                .build();

        assertNull(call.getInput());
    }

    @Test
    @DisplayName("output is optional and defaults to null when not set")
    void outputIsOptional() {
        ToolCall call = ToolCall.builder()
                .toolName("send_email")
                .callId("call_def456")
                .input("{\"to\":\"user@example.com\"}")
                .build();

        assertNull(call.getOutput());
    }

    @Test
    @DisplayName("build() throws IllegalStateException when toolName is null")
    void nullToolNameThrows() {
        assertThrows(IllegalStateException.class, () ->
                ToolCall.builder()
                        .callId("call_001")
                        .build(),
                "build() should throw when toolName is null");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when toolName is blank")
    void blankToolNameThrows() {
        assertThrows(IllegalStateException.class, () ->
                ToolCall.builder()
                        .toolName("  ")
                        .callId("call_002")
                        .build(),
                "build() should throw when toolName is blank");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when callId is null")
    void nullCallIdThrows() {
        assertThrows(IllegalStateException.class, () ->
                ToolCall.builder()
                        .toolName("my_tool")
                        .build(),
                "build() should throw when callId is null");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when callId is blank")
    void blankCallIdThrows() {
        assertThrows(IllegalStateException.class, () ->
                ToolCall.builder()
                        .toolName("my_tool")
                        .callId("")
                        .build(),
                "build() should throw when callId is empty string");
    }

    @Test
    @DisplayName("builder can build minimal instance with only required fields")
    void minimalBuild() {
        ToolCall call = ToolCall.builder()
                .toolName("noop")
                .callId("call_min")
                .build();

        assertEquals("noop", call.getToolName());
        assertEquals("call_min", call.getCallId());
        assertNull(call.getInput());
        assertNull(call.getOutput());
    }
}
