package dev.amissouri.hcg.npcs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;

final class SkinFetcher {

    record Skin(String value, String signature) {}

    private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SkinFetcher() {
    }

    static boolean looksLikeUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://");
    }

    static CompletableFuture<Skin> fetch(String input, String mineSkinApiKey) {
        return looksLikeUrl(input) ? fetchByUrl(input, mineSkinApiKey) : fetchByName(input);
    }

    private static CompletableFuture<Skin> fetchByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = Bukkit.createProfile(name);
            if (!profile.complete(true)) {
                throw new IllegalStateException("No Mojang account named '" + name + "' was found.");
            }
            for (ProfileProperty property : profile.getProperties()) {
                if (property.getName().equals("textures") && property.getSignature() != null) {
                    return new Skin(property.getValue(), property.getSignature());
                }
            }
            throw new IllegalStateException("Mojang returned no skin for '" + name + "'.");
        });
    }

    private static CompletableFuture<Skin> fetchByUrl(String url, String apiKey) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mineskin.org/generate/url"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("User-Agent", "HCGplugin")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"url\":\"" + url.replace("\"", "") + "\",\"visibility\":0}"));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 429) {
                        throw new IllegalStateException(
                                "MineSkin rate limit hit, try again in a minute"
                                        + " (a mineskin-api-key in config.yml raises the limit).");
                    }
                    Matcher value = VALUE.matcher(response.body());
                    Matcher signature = SIGNATURE.matcher(response.body());
                    if (response.statusCode() / 100 != 2 || !value.find() || !signature.find()) {
                        throw new IllegalStateException(
                                "MineSkin could not create a skin from that URL (HTTP "
                                        + response.statusCode() + ").");
                    }
                    return new Skin(value.group(1), signature.group(1));
                });
    }
}
