package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.annotation.command.Subcommand;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.core.util.Levenshtein;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AnnotationCommandProcessor {

    private static final int MAX_SUGGEST_DISTANCE = 3;

    private final INixPermsAPI api;
    private final PermissionNodeCatalog permissionCatalog;
    private final ConcurrentHashMap<String, RegisteredSubcommand> subcommands = new ConcurrentHashMap<>();
    private final Set<String> primaryLabels = ConcurrentHashMap.newKeySet();

    public AnnotationCommandProcessor(INixPermsAPI api) {
        this.api = api;
        this.permissionCatalog = new PermissionNodeCatalog(api);
    }

    public void register(Object subcommand) {
        Subcommand annotation = subcommand.getClass().getAnnotation(Subcommand.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class " + subcommand.getClass().getSimpleName() + " is not annotated with @Subcommand"
            );
        }

        RegisteredSubcommand registered = new RegisteredSubcommand(
                subcommand, annotation, api, permissionCatalog
        );
        String label = annotation.label().toLowerCase(Locale.ROOT);
        subcommands.put(label, registered);
        primaryLabels.add(label);
        permissionCatalog.register(annotation.permission());
        for (String alias : annotation.aliases()) {
            subcommands.put(alias.toLowerCase(Locale.ROOT), registered);
        }
    }

    public void execute(NixCommandSender sender, String[] args) {
        if (args.length == 0) {
            api.messages().send(sender, "commands.usage", Map.of());
            return;
        }

        String label = args[0].toLowerCase(Locale.ROOT);
        RegisteredSubcommand subcommand = subcommands.get(label);
        if (subcommand != null) {
            subcommand.execute(sender, args);
            return;
        }

        String closest = findClosest(label);
        if (closest != null) {
            api.messages().send(sender, "commands.unknown-did-you-mean", Map.of(
                    "input", args[0], "suggestion", closest
            ));
            return;
        }
        api.messages().send(sender, "commands.unknown", Map.of("input", args[0]));
    }

    public List<String> suggest(NixCommandSender sender, String[] args) {
        if (args == null || args.length <= 1) {
            String input = args == null || args.length == 0 ? "" : args[0];
            return NixCommandSuggestions.filter(primaryLabels, input);
        }
        RegisteredSubcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) return NixCommandSuggestions.filter(primaryLabels, args[0]);
        return subcommand.suggest(sender, args);
    }

    private String findClosest(String input) {
        return primaryLabels.stream()
                .map(label -> new Candidate(label, Levenshtein.distance(label, input, MAX_SUGGEST_DISTANCE)))
                .filter(candidate -> candidate.distance() <= MAX_SUGGEST_DISTANCE)
                .min(Comparator.comparingInt(Candidate::distance)
                        .thenComparing(Candidate::label))
                .map(Candidate::label)
                .orElse(null);
    }

    private record Candidate(String label, int distance) { }
}
