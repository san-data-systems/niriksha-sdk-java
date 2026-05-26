package ai.niriksha.sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaggageHelperTest {

    @Test
    @DisplayName("set() returns a Context containing the given baggage entry")
    void setReturnsBaggageInContext() {
        Context ctx = BaggageHelper.set("tenant.id", "acme-corp");

        assertNotNull(ctx, "set() must return a non-null Context");

        try (Scope ignored = ctx.makeCurrent()) {
            String value = Baggage.current().getEntryValue("tenant.id");
            assertEquals("acme-corp", value,
                    "The baggage entry should be readable from the returned context");
        }
    }

    @Test
    @DisplayName("get() returns the value when the key is present in current baggage")
    void getReturnsValueWhenPresent() {
        Context ctx = BaggageHelper.set("x-request-id", "req-12345");

        try (Scope ignored = ctx.makeCurrent()) {
            assertEquals("req-12345", BaggageHelper.get("x-request-id"));
        }
    }

    @Test
    @DisplayName("get() returns empty string when the key is not present in current baggage")
    void getReturnsEmptyStringWhenAbsent() {
        String result = BaggageHelper.get("nonexistent.key");
        assertEquals("", result, "Missing baggage key must return empty string, not null");
    }

    @Test
    @DisplayName("set() can store multiple independent entries in successive contexts")
    void setMultipleEntries() {
        Context ctx1 = BaggageHelper.set("env", "production");
        try (Scope ignored1 = ctx1.makeCurrent()) {
            Context ctx2 = BaggageHelper.set("feature-flag", "dark-mode");
            try (Scope ignored2 = ctx2.makeCurrent()) {
                assertEquals("production", BaggageHelper.get("env"),
                        "Earlier entry should still be visible after adding a second one");
                assertEquals("dark-mode", BaggageHelper.get("feature-flag"),
                        "Newly added entry should be visible");
            }
        }
    }

    @Test
    @DisplayName("get() is isolated to the current context — entry is not visible outside the scope")
    void baggageNotVisibleOutsideScope() {
        Context ctx = BaggageHelper.set("scoped.key", "some-value");

        try (Scope ignored = ctx.makeCurrent()) {
            assertEquals("some-value", BaggageHelper.get("scoped.key"));
        }

        // After the scope closes the current context reverts — the entry should be gone
        assertEquals("", BaggageHelper.get("scoped.key"),
                "Baggage entry must not leak outside its scope");
    }

    @Test
    @DisplayName("set() returns a new Context instance without mutating the existing context")
    void setDoesNotMutateCurrentContext() {
        Context before = Context.current();
        BaggageHelper.set("immutable-check", "value");
        // The original context must not have been mutated
        assertNull(Baggage.fromContext(before).getEntryValue("immutable-check"),
                "set() must not mutate the context it was called from");
    }
}
