package com.eshant.insightrag.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, free, offline {@link LlmClient} — the default so the whole project runs with no API
 * key and no cost.
 *
 * <p>It is <b>extractive</b>: it parses the numbered context out of a {@link PromptBuilder} prompt,
 * selects the sentences whose words best overlap the question, and returns them verbatim with their
 * citation markers. Because it only ever quotes retrieved text, its answers are
 * grounded-by-construction — it cannot hallucinate. That honesty matters for the eval: the always-real
 * before/after gains come from retrieval quality and abstention, not from a model pretending to reason.
 * If no context sentence is relevant, it returns {@link PromptBuilder#ABSTAIN_ANSWER}.
 */
public class MockLlmClient implements LlmClient {

    private static final Pattern WORD = Pattern.compile("[^\\p{Alnum}_]+");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");
    /** Matches a context line like "[2] (source.md) some text". */
    private static final Pattern CONTEXT_LINE = Pattern.compile("^\\[(\\d+)]\\s*\\([^)]*\\)\\s*(.*)$");
    /** Number of top sentences to stitch into the answer. */
    private static final int MAX_SENTENCES = 2;

    @Override
    public String generate(String prompt) {
        List<ContextEntry> context = parseContext(prompt);
        String question = parseQuestion(prompt);
        if (context.isEmpty() || question.isEmpty()) {
            return PromptBuilder.ABSTAIN_ANSWER;
        }

        Set<String> queryTerms = tokenize(question);
        List<ScoredSentence> scored = new ArrayList<>();
        for (ContextEntry entry : context) {
            for (String sentence : SENTENCE.split(entry.text())) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int overlap = overlap(trimmed, queryTerms);
                if (overlap > 0) {
                    scored.add(new ScoredSentence(trimmed, entry.number(), overlap));
                }
            }
        }
        if (scored.isEmpty()) {
            return PromptBuilder.ABSTAIN_ANSWER;
        }

        // Highest overlap first; ties keep earlier (higher-ranked) context order via stable sort.
        scored.sort((a, b) -> Integer.compare(b.overlap(), a.overlap()));

        StringBuilder answer = new StringBuilder();
        Set<Integer> citationsUsed = new HashSet<>();
        for (int i = 0; i < scored.size() && i < MAX_SENTENCES; i++) {
            ScoredSentence s = scored.get(i);
            if (answer.length() > 0) {
                answer.append(' ');
            }
            answer.append(s.text());
            if (citationsUsed.add(s.citation())) {
                answer.append(" [").append(s.citation()).append(']');
            }
        }
        return answer.toString();
    }

    @Override
    public String name() {
        return "mock-extractive";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    private static List<ContextEntry> parseContext(String prompt) {
        List<ContextEntry> entries = new ArrayList<>();
        boolean inContext = false;
        for (String line : prompt.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals(PromptBuilder.CONTEXT_HEADER)) {
                inContext = true;
                continue;
            }
            if (trimmed.startsWith(PromptBuilder.QUESTION_HEADER)) {
                break;
            }
            if (inContext) {
                var m = CONTEXT_LINE.matcher(trimmed);
                if (m.matches()) {
                    entries.add(new ContextEntry(Integer.parseInt(m.group(1)), m.group(2)));
                }
            }
        }
        return entries;
    }

    private static String parseQuestion(String prompt) {
        for (String line : prompt.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(PromptBuilder.QUESTION_HEADER)) {
                return trimmed.substring(PromptBuilder.QUESTION_HEADER.length()).trim();
            }
        }
        return "";
    }

    private static int overlap(String sentence, Set<String> queryTerms) {
        int count = 0;
        for (String term : tokenize(sentence)) {
            if (queryTerms.contains(term)) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String raw : WORD.split(text.toLowerCase())) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    /** A parsed context line: its bracket number and its text. */
    private record ContextEntry(int number, String text) {
    }

    /** A candidate sentence with its source citation number and query-overlap score. */
    private record ScoredSentence(String text, int citation, int overlap) {
    }
}
