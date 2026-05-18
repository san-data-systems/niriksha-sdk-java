package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that demonstrates custom OpenTelemetry span creation.
 *
 * <p>Every endpoint creates a child span with domain-specific attributes so that
 * NirikshaAI can surface them in the trace detail view and AI query assistant.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final String INSTRUMENTATION_SCOPE = "com.example.demo";

    // Reuse the tracer across requests — Tracer is thread-safe
    private final Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE, "0.1.0");

    /**
     * GET /orders/{id} — fetches a single order.
     *
     * <p>Creates a custom span named {@code orders.get} and adds structured attributes
     * (order ID, customer ID, status) that appear as span tags in NirikshaAI.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String id) {
        // Start a custom child span. The parent span (HTTP server span) is propagated
        // automatically via OTel context.
        Span span = tracer.spanBuilder("orders.get")
                .setAttribute("order.id", id)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate order lookup
            Map<String, Object> order = fetchOrder(id, span);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch order " + id));
        } finally {
            span.end();
        }
    }

    /**
     * GET /orders/{id}/items — fetches line items for an order.
     *
     * <p>Demonstrates recording span events (structured log entries attached to the span)
     * in addition to span attributes.
     */
    @GetMapping("/{id}/items")
    public ResponseEntity<Map<String, Object>> getOrderItems(@PathVariable String id) {
        Span span = tracer.spanBuilder("orders.items.list")
                .setAttribute("order.id", id)
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            List<Map<String, Object>> items = fetchOrderItems(id, span);

            // Record a span event — appears as a timestamped log entry on the trace
            span.addEvent("items.fetched", Attributes.of(
                    AttributeKey.longKey("item.count"), (long) items.size()));

            return ResponseEntity.ok(Map.of("orderId", id, "items", items));
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch items for order " + id));
        } finally {
            span.end();
        }
    }

    // -------------------------------------------------------------------------
    // Stub data helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> fetchOrder(String id, Span span) {
        // In a real application this would call a database or downstream service.
        // The span attributes set here appear in NirikshaAI trace detail.
        Map<String, Object> order = Map.of(
                "id", id,
                "customerId", "cust-" + id,
                "status", "SHIPPED",
                "totalCents", 4999
        );
        span.setAttribute("order.customer_id", "cust-" + id);
        span.setAttribute("order.status", "SHIPPED");
        span.setAttribute("order.total_cents", 4999L);
        return order;
    }

    private List<Map<String, Object>> fetchOrderItems(String id, Span span) {
        List<Map<String, Object>> items = List.of(
                Map.of("sku", "WIDGET-A", "qty", 2, "unitCents", 999),
                Map.of("sku", "GADGET-B", "qty", 1, "unitCents", 3001)
        );
        span.setAttribute("db.system", "postgresql");
        span.setAttribute("db.operation", "SELECT");
        span.setAttribute("db.sql.table", "order_items");
        return items;
    }
}
