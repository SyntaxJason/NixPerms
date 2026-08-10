package de.astranox.nixperms.core.profile;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangProfileResolver implements IProfileResolver {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long POSITIVE_CACHE_NANOS = Duration.ofHours(6).toNanos();
    private static final long NEGATIVE_CACHE_NANOS = Duration.ofMinutes(5).toNanos();
    private static final List<String> ENDPOINTS = List.of(
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
            "https://api.mojang.com/users/profiles/minecraft/"
    );

    private final HttpClient client;
    private final List<String> endpoints;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<@Nullable ResolvedProfile>> pending = new ConcurrentHashMap<>();

    public MojangProfileResolver() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build(), ENDPOINTS);
    }

    MojangProfileResolver(HttpClient client, List<String> endpoints) {
        if (client == null) throw new IllegalArgumentException("HTTP client cannot be null");
        if (endpoints == null || endpoints.isEmpty()) throw new IllegalArgumentException("Profile endpoints cannot be empty");
        this.client = client;
        this.endpoints = List.copyOf(endpoints);
    }

    @Override
    public CompletableFuture<@Nullable ResolvedProfile> resolve(String name) {
        if (!validName(name)) return CompletableFuture.completedFuture(null);
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(normalized);
        long now = System.nanoTime();
        if (cached != null) {
            if (cached.expiresAtNanos() > now) return CompletableFuture.completedFuture(cached.profile());
            cache.remove(normalized, cached);
        }

        return pending.computeIfAbsent(normalized, ignored -> startLookup(name.trim(), normalized));
    }

    private CompletableFuture<@Nullable ResolvedProfile> startLookup(String requestedName, String cacheKey) {
        CompletableFuture<@Nullable ResolvedProfile> future = lookup(requestedName, 0)
                .thenApply(profile -> {
                    long ttl = profile == null ? NEGATIVE_CACHE_NANOS : POSITIVE_CACHE_NANOS;
                    cache.put(cacheKey, new CacheEntry(profile, System.nanoTime() + ttl));
                    return profile;
                });
        future.whenComplete((result, error) -> pending.remove(cacheKey, future));
        return future;
    }

    private CompletableFuture<@Nullable ResolvedProfile> lookup(String name, int endpointIndex) {
        if (endpointIndex >= endpoints.size()) return CompletableFuture.completedFuture(null);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoints.get(endpointIndex) + name))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "NixPerms/0.1.0")
                .GET()
                .build();

        CompletableFuture<@Nullable ResolvedProfile> result = new CompletableFuture<>();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
                if (endpointIndex + 1 < endpoints.size()) {
                    pipe(lookup(name, endpointIndex + 1), result);
                    return;
                }
                result.completeExceptionally(profileFailure(error));
                return;
            }

            int status = response.statusCode();
            if (status == 200) {
                ResolvedProfile profile = parseProfile(response.body());
                if (profile != null) {
                    result.complete(profile);
                    return;
                }
                result.completeExceptionally(new IllegalStateException("Mojang returned an invalid profile response"));
                return;
            }
            if (status == 204 || status == 404) {
                result.complete(null);
                return;
            }
            if (endpointIndex + 1 < endpoints.size() && (status == 400 || status == 403 || status >= 500)) {
                pipe(lookup(name, endpointIndex + 1), result);
                return;
            }
            result.completeExceptionally(
                    new IllegalStateException("Mojang profile lookup failed with HTTP " + status)
            );
        });
        return result;
    }

    private static <T> void pipe(CompletableFuture<T> source, CompletableFuture<T> target) {
        source.whenComplete((value, error) -> {
            if (error != null) {
                target.completeExceptionally(error);
                return;
            }
            target.complete(value);
        });
    }

    static @Nullable ResolvedProfile parseProfile(String json) {
        if (json == null || json.isBlank()) return null;
        String id = jsonString(json, "id");
        String name = jsonString(json, "name");
        if (id == null || name == null) return null;

        try {
            return new ResolvedProfile(parseUuid(id), name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        String normalized = value.trim();
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + '-' +
                    normalized.substring(8, 12) + '-' +
                    normalized.substring(12, 16) + '-' +
                    normalized.substring(16, 20) + '-' +
                    normalized.substring(20);
        }
        return UUID.fromString(normalized);
    }

    private static @Nullable String jsonString(String json, String field) {
        String marker = '"' + field + '"';
        int key = json.indexOf(marker);
        if (key < 0) return null;
        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) return null;
        int quote = skipWhitespace(json, colon + 1);
        if (quote >= json.length() || json.charAt(quote) != '"') return null;

        StringBuilder value = new StringBuilder(32);
        for (int index = quote + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character == '"') return value.toString();
            if (character != '\\') {
                value.append(character);
                continue;
            }
            if (++index >= json.length()) return null;
            char escaped = json.charAt(index);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (index + 4 >= json.length()) return null;
                    try {
                        value.append((char) Integer.parseInt(json.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                    index += 4;
                }
                default -> { return null; }
            }
        }
        return null;
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index;
    }

    private static boolean validName(String value) {
        if (value == null) return false;
        String name = value.trim();
        if (name.isEmpty() || name.length() > 16) return false;
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            boolean valid = character == '_' ||
                    character >= '0' && character <= '9' ||
                    character >= 'a' && character <= 'z' ||
                    character >= 'A' && character <= 'Z';
            if (!valid) return false;
        }
        return true;
    }

    private static RuntimeException profileFailure(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return new IllegalStateException("Could not reach the Mojang profile service", cause);
    }

    private record CacheEntry(@Nullable ResolvedProfile profile, long expiresAtNanos) { }
}
