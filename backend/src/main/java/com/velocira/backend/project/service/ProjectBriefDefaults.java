package com.velocira.backend.project.service;

import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.model.ProjectType;

import java.util.Locale;

/** Deterministic defaults keep the first project request small and predictable. */
public final class ProjectBriefDefaults {

    private ProjectBriefDefaults() {
    }

    public static String title(ProjectBriefRequest request) {
        if (hasText(request.title())) return limit(capitalise(request.title().trim()), 255);
        String firstSentence = request.brief().trim().split("[.!?\\n]", 2)[0]
                .replaceFirst("(?i)^(build|create|make|design)\\s+", "")
                .replaceFirst("(?i)^(an?|the)\\s+", "").trim();
        if (firstSentence.isBlank()) firstSentence = "New project";
        return limit(capitalise(firstSentence), 72);
    }

    public static ProjectType type(String brief) {
        String source = brief.toLowerCase(Locale.ROOT);
        if (contains(source, "mobile", "ios", "android", "iphone", "ipad")) return ProjectType.MOBILE_APP;
        if (contains(source, "api", "backend", "integration service")) return ProjectType.API_BACKEND;
        if (source.matches(".*\\bai\\b.*") || contains(source, "assistant", "copilot", "agent", "llm", "machine learning")) return ProjectType.AI_SYSTEM;
        if (contains(source, "iot", "sensor", "device firmware", "embedded")) return ProjectType.IOT;
        if (contains(source, "desktop", "windows app", "mac app")) return ProjectType.DESKTOP_APP;
        return ProjectType.WEB_APP;
    }

    public static String description(ProjectBriefRequest request) {
        StringBuilder description = new StringBuilder(request.brief().trim());
        append(description, "Audience", request.audience());
        append(description, "Important to include", request.include());
        append(description, "Avoid", request.avoid());
        return limit(description.toString(), 2_000);
    }

    private static void append(StringBuilder target, String label, String value) {
        if (hasText(value)) target.append("\n\n").append(label).append(": ").append(value.trim());
    }

    private static boolean contains(String source, String... terms) {
        for (String term : terms) if (source.contains(term)) return true;
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1).trim() + "…";
    }
}
