package com.eshant.insightrag.agent;

import com.eshant.insightrag.generation.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides whether a question should be answered from the documentation (RAG) or from the structured
 * release table (SQL) — the agent's routing brain.
 *
 * <p>By default it routes with transparent rules (so it works for free with the mock), keying off
 * signals that mark a request for precise tabular facts: a component name, a version number, or
 * phrases like "release date" / "latest version" / "min java". When a real LLM is configured it asks
 * the model to classify instead, falling back to the rules if the reply is unclear. Either way the
 * decision and its reason are logged.
 */
@Component
public class IntentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);

    private static final Pattern COMPONENT = Pattern.compile("nimbus-[a-z]+");
    private static final Pattern VERSION = Pattern.compile("\\b\\d+\\.\\d+(\\.\\d+)?\\b");
    private static final List<String> KEYWORDS = List.of(
            "release date", "released", "release", "latest version", "newest", "latest",
            "which version", "what version", "version of", "min java", "minimum java", "java version",
            "when was", "when did", "lts", "beta", "stable", "release notes");

    private final LlmClient llm;

    public IntentRouter(LlmClient llm) {
        this.llm = llm;
    }

    /** Routes a question, logging the chosen intent and why. */
    public RouteDecision route(String question) {
        RouteDecision decision = llm.isMock() ? routeByRules(question) : routeByLlm(question);
        log.info("Route [{}] intent={} ({}): \"{}\"",
                decision.llmRouted() ? "llm" : "rules", decision.intent(), decision.reason(), question);
        return decision;
    }

    /** Deterministic rule-based routing — the default and the fallback. */
    RouteDecision routeByRules(String question) {
        String q = question.toLowerCase();
        List<String> signals = new ArrayList<>();

        boolean hasComponent = COMPONENT.matcher(q).find();
        boolean hasVersion = VERSION.matcher(q).find();
        if (hasComponent) {
            signals.add("component-name");
        }
        if (hasVersion) {
            signals.add("version-number");
        }
        for (String kw : KEYWORDS) {
            if (q.contains(kw)) {
                signals.add("kw:" + kw);
            }
        }

        // Status keywords (lts/beta) are strong stand-alone signals of a release-table query.
        boolean hasStatusSignal = q.contains("lts") || q.contains("beta");
        if (hasStatusSignal) {
            signals.add("status");
        }

        // Structured if a component is named alongside any other signal, a strong stand-alone phrase
        // is present (e.g. "what's the latest version", "release date of ..."), or a status is asked.
        boolean strongPhrase = q.contains("release date") || q.contains("latest version")
                || q.contains("newest") || q.contains("when was") || q.contains("min java")
                || q.contains("minimum java");
        boolean structured = (hasComponent && signals.size() > 1) || strongPhrase
                || (hasComponent && hasVersion) || hasStatusSignal;

        Intent intent = structured ? Intent.STRUCTURED_DATA : Intent.DOC_QA;
        String reason = signals.isEmpty() ? "no structured signals" : "signals=" + signals;
        return new RouteDecision(intent, reason, false);
    }

    /** LLM-based routing, used only when a real model is configured. */
    RouteDecision routeByLlm(String question) {
        String prompt = "Classify the question. Reply with ONLY one word: SQL or RAG.\n"
                + "SQL = needs exact structured facts (component versions, release dates, Java "
                + "requirements) from a release table.\n"
                + "RAG = a conceptual or how-to question answered from prose documentation.\n\n"
                + "QUESTION: " + question + "\nANSWER:";
        String reply;
        try {
            reply = llm.generate(prompt).toLowerCase();
        } catch (RuntimeException e) {
            RouteDecision fallback = routeByRules(question);
            return new RouteDecision(fallback.intent(), "llm error, fell back to rules: " + fallback.reason(), false);
        }
        if (reply.contains("sql")) {
            return new RouteDecision(Intent.STRUCTURED_DATA, "llm classified as SQL", true);
        }
        if (reply.contains("rag")) {
            return new RouteDecision(Intent.DOC_QA, "llm classified as RAG", true);
        }
        RouteDecision fallback = routeByRules(question);
        return new RouteDecision(fallback.intent(), "llm unclear, used rules: " + fallback.reason(), false);
    }
}
