package ai.niriksha.sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;

/**
 * Utility methods for working with OpenTelemetry Baggage.
 *
 * <p>Baggage is a set of name-value pairs that propagate across service boundaries
 * alongside trace context. It is useful for carrying correlation IDs, tenant
 * identifiers, and feature flags without modifying business method signatures.
 *
 * <p>Example — setting a tenant ID before an outbound call:
 * <pre>{@code
 * try (Scope scope = BaggageHelper.set("tenant.id", tenantId).makeCurrent()) {
 *     downstreamClient.call();
 * }
 * }</pre>
 *
 * <p>Example — reading a value propagated from an upstream service:
 * <pre>{@code
 * String tenantId = BaggageHelper.get("tenant.id");
 * }</pre>
 *
 * <p><b>Important:</b> Baggage is propagated in the clear (e.g. as a W3C
 * {@code baggage} HTTP header). Do not store secrets or PII in Baggage.
 */
public final class BaggageHelper {

    private BaggageHelper() {}

    /**
     * Returns a new {@link Context} with the given Baggage entry added to the
     * current context's Baggage.
     *
     * <p>Typical usage:
     * <pre>{@code
     * try (Scope scope = BaggageHelper.set("x-tenant-id", tenantId).makeCurrent()) {
     *     // downstream calls will carry this baggage entry
     * }
     * }</pre>
     *
     * @param key   the Baggage entry name; must not be null or blank
     * @param value the Baggage entry value; must not be null
     * @return a new {@link Context} with the entry merged into the current Baggage
     */
    public static Context set(String key, String value) {
        Baggage updated = Baggage.current()
                .toBuilder()
                .put(key, value)
                .build();
        return updated.storeInContext(Context.current());
    }

    /**
     * Gets a Baggage value from the current context.
     *
     * @param key the Baggage entry name to look up
     * @return the entry value, or {@code ""} if the key is absent or the value is null
     */
    public static String get(String key) {
        String value = Baggage.current().getEntryValue(key);
        return value != null ? value : "";
    }
}
