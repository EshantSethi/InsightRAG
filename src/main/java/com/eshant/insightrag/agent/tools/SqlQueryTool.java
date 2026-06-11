package com.eshant.insightrag.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's structured-data tool: answers precise release questions from the {@code nimbus_release}
 * table. It is strictly read-only — every method runs a parameterized {@code SELECT} via
 * {@link JdbcTemplate}, so there is no SQL-injection surface and no way to mutate data.
 *
 * <p>Rather than let a model emit raw SQL, it extracts the component / version / status from the
 * question with simple rules and dispatches to one of a few curated queries. That keeps it safe and
 * deterministic, so it works with the free mock LLM and gives the eval stable, checkable answers.
 */
@Component
public class SqlQueryTool {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryTool.class);

    private static final Pattern VERSION = Pattern.compile("\\b(\\d+\\.\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern COMPONENT = Pattern.compile("nimbus-[a-z]+");
    /** Bare shorthands ("core", "data") mapped to full component names. */
    private static final Map<String, String> SHORTHAND = Map.of(
            "core", "nimbus-core", "data", "nimbus-data", "cache", "nimbus-cache", "web", "nimbus-web");

    private final JdbcTemplate jdbc;

    public SqlQueryTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Resolves the question to one of the curated queries; returns {@link SqlAnswer#notFound()} if none fit. */
    public SqlAnswer answer(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        String component = detectComponent(q);
        String version = detectVersion(q);
        String status = detectStatus(q);
        boolean wantsLatest = q.contains("latest") || q.contains("newest") || q.contains("current");

        // 1. Specific component + version -> exact row.
        if (component != null && version != null) {
            return specificRelease(component, version);
        }
        // 2. Status query (e.g. "which components are LTS / in beta").
        if (status != null && component == null) {
            return byStatus(status);
        }
        // 3. Component, latest/newest or a date/"when" question -> latest release.
        if (component != null && (wantsLatest || q.contains("when") || q.contains("date")
                || q.contains("release"))) {
            return latestRelease(component);
        }
        // 4. Component only -> list all its versions.
        if (component != null) {
            return allVersions(component);
        }
        log.info("SQL tool: no query matched question \"{}\"", question);
        return SqlAnswer.notFound();
    }

    private SqlAnswer specificRelease(String component, String version) {
        String sql = "SELECT release_date, status, min_java FROM nimbus_release "
                + "WHERE component = ? AND version = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, component, version);
        if (rows.isEmpty()) {
            return SqlAnswer.notFound();
        }
        Map<String, Object> r = rows.get(0);
        String text = "%s %s was released on %s (%s, requires Java %s+).".formatted(
                component, version, r.get("release_date"),
                String.valueOf(r.get("status")).toUpperCase(Locale.ROOT), r.get("min_java"));
        return new SqlAnswer(true, text, sql);
    }

    private SqlAnswer latestRelease(String component) {
        String sql = "SELECT version, release_date, status, min_java FROM nimbus_release "
                + "WHERE component = ? ORDER BY release_date DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, component);
        if (rows.isEmpty()) {
            return SqlAnswer.notFound();
        }
        Map<String, Object> r = rows.get(0);
        String text = "The latest %s release is %s, released %s (%s, Java %s+).".formatted(
                component, r.get("version"), r.get("release_date"),
                String.valueOf(r.get("status")).toUpperCase(Locale.ROOT), r.get("min_java"));
        return new SqlAnswer(true, text, sql);
    }

    private SqlAnswer byStatus(String status) {
        String sql = "SELECT component, version, release_date FROM nimbus_release "
                + "WHERE status = ? ORDER BY release_date DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, status);
        if (rows.isEmpty()) {
            return SqlAnswer.notFound();
        }
        StringBuilder sb = new StringBuilder(status.toUpperCase(Locale.ROOT))
                .append(" releases: ");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            sb.append(r.get("component")).append(' ').append(r.get("version"))
                    .append(" (").append(r.get("release_date")).append(')');
            sb.append(i < rows.size() - 1 ? "; " : ".");
        }
        return new SqlAnswer(true, sb.toString(), sql);
    }

    private SqlAnswer allVersions(String component) {
        String sql = "SELECT version, release_date, status FROM nimbus_release "
                + "WHERE component = ? ORDER BY release_date DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, component);
        if (rows.isEmpty()) {
            return SqlAnswer.notFound();
        }
        StringBuilder sb = new StringBuilder(component).append(" releases: ");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            sb.append(r.get("version")).append(" (").append(r.get("release_date")).append(", ")
                    .append(r.get("status")).append(')');
            sb.append(i < rows.size() - 1 ? "; " : ".");
        }
        return new SqlAnswer(true, sb.toString(), sql);
    }

    private static String detectComponent(String q) {
        Matcher m = COMPONENT.matcher(q);
        if (m.find()) {
            return m.group();
        }
        for (Map.Entry<String, String> e : SHORTHAND.entrySet()) {
            if (Pattern.compile("\\b" + e.getKey() + "\\b").matcher(q).find()) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String detectVersion(String q) {
        Matcher m = VERSION.matcher(q);
        return m.find() ? m.group(1) : null;
    }

    private static String detectStatus(String q) {
        if (q.contains("lts")) {
            return "lts";
        }
        if (q.contains("beta")) {
            return "beta";
        }
        if (q.contains("stable")) {
            return "stable";
        }
        return null;
    }
}
