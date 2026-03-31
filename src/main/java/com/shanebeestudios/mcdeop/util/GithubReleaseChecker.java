package com.shanebeestudios.mcdeop.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GithubReleaseChecker {
    private static final URI LATEST_RELEASE_API = URI.create("https://api.github.com/repos/Timmi6790/McDeob/releases/latest");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * Algorithm:
     * 1) Fetch latest GitHub release JSON.
     * 2) Extract {@code tag_name} and release URL.
     * 3) Parse all numeric groups from current and latest versions.
     * 4) Compare each numeric part lexicographically (missing parts treated as 0).
     * 5) Return update info only when latest > current.
     */
    public Optional<UpdateInfo> checkForUpdate(final String currentVersion) throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        final HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "McDeob/" + currentVersion)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return Optional.empty();
        }

        final String body = response.body();
        final String latestTag = this.matchFirst(TAG_NAME_PATTERN, body);
        if (latestTag == null || !this.isNewer(currentVersion, latestTag)) {
            return Optional.empty();
        }

        final String releaseUrl = this.matchFirst(HTML_URL_PATTERN, body);
        return Optional.of(new UpdateInfo(latestTag, releaseUrl));
    }

    private String matchFirst(final Pattern pattern, final String value) {
        final Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isNewer(final String currentVersion, final String latestVersion) {
        final List<Integer> currentParts = this.parseVersionParts(currentVersion);
        final List<Integer> latestParts = this.parseVersionParts(latestVersion);
        if (currentParts.isEmpty() || latestParts.isEmpty()) {
            return false;
        }

        final int max = Math.max(currentParts.size(), latestParts.size());
        for (int i = 0; i < max; i++) {
            final int current = i < currentParts.size() ? currentParts.get(i) : 0;
            final int latest = i < latestParts.size() ? latestParts.get(i) : 0;
            if (latest > current) {
                return true;
            }
            if (latest < current) {
                return false;
            }
        }
        return false;
    }

    private List<Integer> parseVersionParts(final String version) {
        final List<Integer> parts = new ArrayList<>();
        final Matcher matcher = NUMBER_PATTERN.matcher(version);
        while (matcher.find()) {
            parts.add(Integer.parseInt(matcher.group()));
        }
        return parts;
    }

    public record UpdateInfo(String latestTag, String releaseUrl) {}
}
