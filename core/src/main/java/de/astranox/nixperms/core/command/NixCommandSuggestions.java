package de.astranox.nixperms.core.command;

import de.astranox.nixperms.core.util.Levenshtein;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NixCommandSuggestions {

    private static final int MAX_DISTANCE = 3;
    private static final int MAX_RESULTS = 50;

    private NixCommandSuggestions() { }

    public static List<String> filter(Collection<String> candidates, String input) {
        String lower = normalize(input);
        Set<String> unique = new LinkedHashSet<>(candidates);
        if (lower.isEmpty()) {
            return unique.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(MAX_RESULTS)
                    .toList();
        }

        List<String> prefix = unique.stream()
                .filter(candidate -> normalize(candidate).startsWith(lower))
                .sorted(Comparator
                        .comparingInt((String candidate) -> normalize(candidate).length())
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .toList();
        if (!prefix.isEmpty()) return prefix;

        return fuzzy(unique, lower);
    }

    /**
     * Returns only the next segment of a permission path. Branches always end in a dot so one tab
     * immediately opens the next level instead of forcing the sender to type the separator manually.
     */
    public static List<String> filterPermissionTree(Collection<String> candidates, String input) {
        String lower = normalize(input);
        int separator = lower.lastIndexOf('.');
        String parent = separator < 0 ? "" : lower.substring(0, separator + 1);
        String current = separator < 0 ? lower : lower.substring(separator + 1);

        Map<String, PermissionSegment> segments = new LinkedHashMap<>();
        for (String rawCandidate : candidates) {
            String candidate = normalize(rawCandidate);
            if (candidate.isEmpty() || !candidate.startsWith(parent)) continue;

            String remainder = candidate.substring(parent.length());
            if (remainder.isEmpty()) continue;
            int nextSeparator = remainder.indexOf('.');
            String segment = nextSeparator < 0 ? remainder : remainder.substring(0, nextSeparator);
            if (segment.isEmpty()) continue;

            String value = parent + segment;
            boolean branch = nextSeparator >= 0;
            segments.merge(
                    value,
                    new PermissionSegment(value, segment, branch),
                    (left, right) -> new PermissionSegment(left.value(), left.segment(), left.branch() || right.branch())
            );
        }

        List<String> prefix = segments.values().stream()
                .filter(segment -> segment.segment().startsWith(current))
                .sorted(segmentOrder(current))
                .map(NixCommandSuggestions::completionValue)
                .distinct()
                .limit(MAX_RESULTS)
                .toList();
        if (!prefix.isEmpty()) return prefix;
        if (current.isEmpty()) return List.of();

        return segments.values().stream()
                .map(segment -> new RankedSegment(
                        segment,
                        Levenshtein.distance(segment.segment(), current, MAX_DISTANCE)
                ))
                .filter(ranked -> ranked.distance() <= MAX_DISTANCE)
                .sorted(Comparator.comparingInt(RankedSegment::distance)
                        .thenComparing(ranked -> ranked.segment().value(), String.CASE_INSENSITIVE_ORDER))
                .map(ranked -> completionValue(ranked.segment()))
                .distinct()
                .limit(MAX_RESULTS)
                .toList();
    }

    private static List<String> fuzzy(Collection<String> candidates, String input) {
        return candidates.stream()
                .map(candidate -> new RankedCandidate(
                        candidate,
                        Levenshtein.distance(normalize(candidate), input, MAX_DISTANCE)
                ))
                .filter(candidate -> candidate.distance() <= MAX_DISTANCE)
                .sorted(Comparator.comparingInt(RankedCandidate::distance)
                        .thenComparing(RankedCandidate::value, String.CASE_INSENSITIVE_ORDER))
                .map(RankedCandidate::value)
                .limit(MAX_RESULTS)
                .toList();
    }

    private static Comparator<PermissionSegment> segmentOrder(String current) {
        return Comparator
                .comparingInt((PermissionSegment segment) -> segment.segment().equals(current) ? 0 : 1)
                .thenComparing(PermissionSegment::value, String.CASE_INSENSITIVE_ORDER);
    }

    private static String completionValue(PermissionSegment segment) {
        return segment.branch() ? segment.value() + '.' : segment.value();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RankedCandidate(String value, int distance) { }
    private record PermissionSegment(String value, String segment, boolean branch) { }
    private record RankedSegment(PermissionSegment segment, int distance) { }
}
