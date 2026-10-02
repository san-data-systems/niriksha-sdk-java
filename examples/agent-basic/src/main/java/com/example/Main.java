package com.example;

import ai.niriksha.sdk.NirikshaAI;
import ai.niriksha.sdk.Observe;
import ai.niriksha.sdk.Observe.SpanType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A small agent wrapped with Observe.observe / span / log.
 *
 * <p>Run it against any NirikshaAI install and it shows up on LLM → Runs within a few seconds,
 * with a timeline, a repeated-tool finding (we call {@code search} four times in a row on
 * purpose) and one error.
 *
 * <pre>
 * NIRIKSHA_ENDPOINT=https://app.niriksha.ai NIRIKSHA_API_KEY=nai_... java -jar target/*-jar-with-dependencies.jar
 * </pre>
 */
public final class Main {

    private Main() {
    }

    static String callModel(String prompt) throws InterruptedException {
        Thread.sleep(200);
        return "answer to: " + (prompt.length() > 40 ? prompt.substring(0, 40) : prompt);
    }

    static List<String> search(String query) throws InterruptedException {
        Thread.sleep(100);
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            throw new IllegalStateException("search backend timed out");
        }
        return List.of("doc about " + query);
    }

    public static void main(String[] args) throws Exception {
        NirikshaAI.builder()
                .endpoint(System.getenv("NIRIKSHA_ENDPOINT"))
                .apiKey(System.getenv("NIRIKSHA_API_KEY"))
                .serviceName("example-research-agent")
                .build();

        String question = "Why is the checkout service slow?";
        String answer = Observe.observe("research-agent", () -> {
            Observe.log("info", "agent started", Map.of("question", question));
            String plan = Observe.span("plan", SpanType.LLM, () -> callModel("plan for " + question),
                    Map.of("gen_ai.request.model", "example-model"));
            List<String> docs = new ArrayList<>();
            for (int i = 0; i < 4; i++) { // four consecutive calls → a possible_loop finding at the default threshold
                final int n = i;
                try {
                    docs.addAll(Observe.span("search", SpanType.TOOL, () -> search(plan + " #" + n)));
                } catch (IllegalStateException e) {
                    Observe.log("warn", "search failed, continuing", Map.of("error", e.getMessage()));
                }
            }
            return Observe.span("answer", SpanType.LLM, () -> callModel(String.join(" ", docs)),
                    Map.of("gen_ai.request.model", "example-model"));
        });
        System.out.println(answer);
        NirikshaAI.flush(Duration.ofSeconds(5));
    }
}
