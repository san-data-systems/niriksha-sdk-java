package ai.niriksha.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ServerlessWrapper}.
 *
 * <p>These tests do not require a live OTel SDK. {@link NirikshaAI#flush()} is a no-op
 * when the global SDK has not been initialised, which keeps these tests self-contained.
 */
class ServerlessWrapperTest {

    // -----------------------------------------------------------------------
    // withFlush(Callable<T>) — return-value variant
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("withFlush(Callable) executes the callable and returns its result")
    void callableIsExecutedAndResultReturned() throws Exception {
        Callable<String> handler = () -> "hello-from-lambda";

        String result = ServerlessWrapper.withFlush(handler);

        assertEquals("hello-from-lambda", result);
    }

    @Test
    @DisplayName("withFlush(Callable) executes the callable exactly once")
    void callableIsExecutedExactlyOnce() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<Void> handler = () -> {
            callCount.incrementAndGet();
            return null;
        };

        ServerlessWrapper.withFlush(handler);

        assertEquals(1, callCount.get(), "Callable must be invoked exactly once");
    }

    @Test
    @DisplayName("withFlush(Callable) propagates exceptions thrown by the callable")
    void callableExceptionIsPropagated() {
        Callable<String> handler = () -> {
            throw new RuntimeException("handler-failure");
        };

        assertThrows(Exception.class,
                () -> ServerlessWrapper.withFlush(handler),
                "Exception thrown by callable must propagate out of withFlush");
    }

    @Test
    @DisplayName("withFlush(Callable) does not swallow the original exception message")
    void callableExceptionMessagePreserved() {
        Callable<String> handler = () -> {
            throw new RuntimeException("expected-error");
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ServerlessWrapper.withFlush(handler));

        assertEquals("expected-error", thrown.getMessage(),
                "Original exception message must not be swallowed by the flush in finally");
    }

    @Test
    @DisplayName("withFlush(Callable) returns null when the callable returns null")
    void callableCanReturnNull() throws Exception {
        Callable<Object> handler = () -> null;

        Object result = ServerlessWrapper.withFlush(handler);

        assertNull(result, "null return from callable must be passed through unchanged");
    }

    @Test
    @DisplayName("withFlush(Callable) works with integer return type")
    void callableWithIntegerReturn() throws Exception {
        Callable<Integer> handler = () -> 42;

        Integer result = ServerlessWrapper.withFlush(handler);

        assertEquals(42, result);
    }

    // -----------------------------------------------------------------------
    // withFlush(ThrowingRunnable) — void variant
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("withFlush(ThrowingRunnable) executes the runnable")
    void runnableIsExecuted() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        ServerlessWrapper.ThrowingRunnable handler = () -> executed.set(true);

        ServerlessWrapper.withFlush(handler);

        assertTrue(executed.get(), "ThrowingRunnable body must have been executed");
    }

    @Test
    @DisplayName("withFlush(ThrowingRunnable) executes the runnable exactly once")
    void runnableIsExecutedExactlyOnce() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        ServerlessWrapper.ThrowingRunnable handler = callCount::incrementAndGet;

        ServerlessWrapper.withFlush(handler);

        assertEquals(1, callCount.get(), "Runnable must be invoked exactly once");
    }

    @Test
    @DisplayName("withFlush(ThrowingRunnable) propagates exceptions thrown by the runnable")
    void runnableExceptionIsPropagated() {
        ServerlessWrapper.ThrowingRunnable handler = () -> {
            throw new RuntimeException("runnable-failure");
        };

        assertThrows(Exception.class,
                () -> ServerlessWrapper.withFlush(handler),
                "Exception thrown by ThrowingRunnable must propagate out of withFlush");
    }

    @Test
    @DisplayName("withFlush(ThrowingRunnable) does not swallow the original exception message")
    void runnableExceptionMessagePreserved() {
        ServerlessWrapper.ThrowingRunnable handler = () -> {
            throw new RuntimeException("runnable-error-preserved");
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ServerlessWrapper.withFlush(handler));

        assertEquals("runnable-error-preserved", thrown.getMessage());
    }
}
