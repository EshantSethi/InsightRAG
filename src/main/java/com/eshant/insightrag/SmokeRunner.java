package com.eshant.insightrag;

import com.eshant.insightrag.agent.AgentResponse;
import com.eshant.insightrag.agent.AgentService;
import com.eshant.insightrag.agent.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Developer smoke test: runs a handful of representative questions through the {@link AgentService} at
 * startup, prints the routing + answers, and exits with a non-zero code if any check fails.
 *
 * <p>Active only under the {@code smoke} Spring profile, so it never runs in normal operation. Run it
 * with: {@code mvn spring-boot:run -Dspring-boot.run.profiles=smoke}.
 */
@Component
@Profile("smoke")
public class SmokeRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SmokeRunner.class);

    private final AgentService agent;

    public SmokeRunner(AgentService agent) {
        this.agent = agent;
    }

    /** One expected-behaviour check for a question. */
    private record Check(String question, Intent expectedIntent, String mustContain, String note) {
    }

    @Override
    public void run(String... args) {
        List<Check> checks = List.of(
                new Check("How do I configure dependency injection in Nimbus?",
                        Intent.DOC_QA, null, "doc question -> RAG, should answer with a citation"),
                new Check("What is the latest version of nimbus-core?",
                        Intent.STRUCTURED_DATA, "3.2.0", "factual -> SQL, latest version"),
                new Check("When was nimbus-core 3.1.4 released?",
                        Intent.STRUCTURED_DATA, "2024-06-18", "factual -> SQL, specific release date"),
                new Check("Which releases are LTS?",
                        Intent.STRUCTURED_DATA, "nimbus-core", "factual -> SQL, status query"),
                new Check("How do I deploy Nimbus on Kubernetes with an Istio service mesh?",
                        Intent.DOC_QA, null, "out-of-corpus -> RAG should abstain"));

        int passed = 0;
        System.out.println("\n==================== AGENT SMOKE TEST ====================");
        for (Check c : checks) {
            AgentResponse r = agent.ask(c.question());
            boolean intentOk = r.intent() == c.expectedIntent();
            boolean contentOk = c.mustContain() == null
                    || r.answer().toLowerCase().contains(c.mustContain().toLowerCase());
            boolean pass = intentOk && contentOk;
            if (pass) {
                passed++;
            }
            System.out.printf("%n[%s] %s%n", pass ? "PASS" : "FAIL", c.note());
            System.out.println("  Q: " + c.question());
            System.out.println("  route: " + r.intent() + "  (" + r.routeReason() + ")");
            System.out.println("  answer: " + r.answer());
            if (!r.citations().isEmpty()) {
                System.out.println("  citations: " + r.citations());
            }
            if (r.detail() != null) {
                System.out.println("  sql: " + r.detail());
            }
            if (r.abstained()) {
                System.out.println("  (abstained)");
            }
            if (!intentOk) {
                System.out.println("  !! expected route " + c.expectedIntent());
            }
            if (!contentOk) {
                System.out.println("  !! expected answer to contain: " + c.mustContain());
            }
        }
        System.out.printf("%n==================== %d/%d PASSED ====================%n%n",
                passed, checks.size());

        // Exit so the run terminates instead of leaving the web server up.
        System.exit(passed == checks.size() ? 0 : 1);
    }
}
