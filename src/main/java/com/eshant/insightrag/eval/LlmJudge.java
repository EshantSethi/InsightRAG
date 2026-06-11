package com.eshant.insightrag.eval;

import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.retrieval.RetrievedChunk;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional "LLM-as-judge" faithfulness scorer: asks a real model whether an answer is actually
 * supported by the retrieved context, returning a 0–1 score.
 *
 * <p>This complements the always-on, deterministic {@link com.eshant.insightrag.generation.GroundingChecker}.
 * The rule-based checker is the dependable floor; an LLM judge catches paraphrased or reworded claims
 * that a bag-of-words overlap misses. It is only meaningful with a real model — the extractive
 * {@link com.eshant.insightrag.generation.MockLlmClient} is grounded by construction and cannot judge —
 * so the harness invokes this only when {@link LlmClient#isMock()} is false, and any parse failure
 * degrades gracefully to {@link Double#NaN} (reported as "n/a") rather than corrupting the averages.
 */
public final class LlmJudge {

    private static final Pattern SCORE = Pattern.compile("(?<![0-9.])(0(?:\\.\\d+)?|1(?:\\.0+)?)");

    private final LlmClient llm;

    public LlmJudge(LlmClient llm) {
        this.llm = llm;
    }

    /** True when a real model is available to judge (mock is skipped). */
    public boolean available() {
        return !llm.isMock();
    }

    /**
     * Returns the model's faithfulness rating in [0,1], or {@link Double#NaN} if unavailable or the
     * response can't be parsed.
     */
    public double judge(String question, String answer, List<RetrievedChunk> context) {
        if (!available()) {
            return Double.NaN;
        }
        String response = llm.generate(buildPrompt(question, answer, context));
        return parseScore(response);
    }

    private static String buildPrompt(String question, String answer, List<RetrievedChunk> context) {
        StringBuilder ctx = new StringBuilder();
        for (RetrievedChunk rc : context) {
            ctx.append("- ").append(rc.chunk().text().strip()).append('\n');
        }
        return """
                You are a strict grading judge. Decide how well the ANSWER is supported by the CONTEXT.
                Reply with ONLY a number between 0 and 1 (e.g. 0.0, 0.5, 1.0). 1 = every claim is
                supported by the context; 0 = the answer is unsupported or contradicts the context.

                QUESTION:
                %s

                CONTEXT:
                %s
                ANSWER:
                %s

                Score (0 to 1):""".formatted(question, ctx, answer);
    }

    /** Extracts the first 0–1 value from the model's reply. */
    static double parseScore(String response) {
        if (response == null) {
            return Double.NaN;
        }
        Matcher m = SCORE.matcher(response.trim());
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1));
                if (v >= 0.0 && v <= 1.0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to NaN
            }
        }
        return Double.NaN;
    }
}
