package ai.niriksha.sdk;

import java.util.concurrent.Callable;

/**
 * Utility for serverless and short-lived JVM environments (AWS Lambda, Google Cloud
 * Functions, Azure Functions, Kubernetes Jobs, etc.).
 *
 * <p>In serverless runtimes the JVM process may be frozen or terminated immediately
 * after the handler returns. Calling {@link NirikshaAI#flush()} in a {@code finally}
 * block ensures that spans, metrics, and log records buffered by the OTLP batch
 * processors are exported before the runtime recycles the instance.
 *
 * <p>Example — Lambda handler with a return value:
 * <pre>{@code
 * public APIGatewayProxyResponseEvent handleRequest(
 *         APIGatewayProxyRequestEvent event, Context context) throws Exception {
 *     return ServerlessWrapper.withFlush(() -> {
 *         // handler logic
 *         return buildResponse(200, "ok");
 *     });
 * }
 * }</pre>
 *
 * <p>Example — void handler:
 * <pre>{@code
 * public void handleRequest(SQSEvent event, Context context) throws Exception {
 *     ServerlessWrapper.withFlush(() -> processBatch(event.getRecords()));
 * }
 * }</pre>
 */
public final class ServerlessWrapper {

    private ServerlessWrapper() {}

    /**
     * Executes the given {@link Callable} and force-flushes all pending telemetry
     * afterwards, even if the callable throws.
     *
     * <p>{@link NirikshaAI#flush()} is always called in the {@code finally} block.
     * If the SDK has not been initialised, the flush call is a no-op.
     *
     * @param <T>      return type of the callable
     * @param callable the handler logic to execute
     * @return the value returned by the callable
     * @throws Exception if the callable throws
     */
    public static <T> T withFlush(Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } finally {
            NirikshaAI.flush();
        }
    }

    /**
     * Executes the given {@link ThrowingRunnable} and force-flushes all pending
     * telemetry afterwards, even if the runnable throws.
     *
     * <p>{@link NirikshaAI#flush()} is always called in the {@code finally} block.
     * If the SDK has not been initialised, the flush call is a no-op.
     *
     * @param runnable the handler logic to execute
     * @throws Exception if the runnable throws
     */
    public static void withFlush(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
        } finally {
            NirikshaAI.flush();
        }
    }

    /**
     * A {@link Runnable}-like interface whose {@link #run()} method is permitted to
     * throw checked exceptions, allowing lambda-friendly use in handler methods that
     * declare {@code throws Exception}.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        /**
         * Executes the handler logic.
         *
         * @throws Exception if an error occurs
         */
        void run() throws Exception;
    }
}
