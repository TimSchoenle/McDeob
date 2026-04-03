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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GithubReleaseChecker {
    private static final URI LATEST_RELEASE_API =
            URI.create("https://api.github.com/repos/" + GeneratedConstant.GITHUB_REPO_NAME + "/releases/latest");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile("\"published_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY_PATTERN =
            Pattern.compile("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
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
        final UpdateCheckResult result = this.checkForUpdateDetailed(currentVersion);
        return result.status() == UpdateCheckStatus.UPDATE_AVAILABLE
                ? Optional.ofNullable(result.updateInfo())
                : Optional.empty();
    }

    public UpdateCheckResult checkForUpdateDetailed(final String currentVersion)
            throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        final HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "McDeob/" + currentVersion)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return new UpdateCheckResult(
                    UpdateCheckStatus.FAILED, null, "GitHub API returned HTTP " + response.statusCode());
        }

        final String body = response.body();
        final String latestTag = this.matchFirst(TAG_NAME_PATTERN, body);
        if (latestTag == null || latestTag.isBlank()) {
            return new UpdateCheckResult(UpdateCheckStatus.FAILED, null, "Could not parse release tag from response");
        }

        final String releaseUrl = this.matchFirst(HTML_URL_PATTERN, body);
        final String releaseName = this.matchFirst(NAME_PATTERN, body);
        final String publishedAt = this.matchFirst(PUBLISHED_AT_PATTERN, body);
        final String releaseBodyRaw = this.matchFirst(BODY_PATTERN, body);
        final String releaseBody = releaseBodyRaw != null ? this.unescapeJsonString(releaseBodyRaw) : null;
        final UpdateInfo info = new UpdateInfo(latestTag, releaseUrl, releaseName, publishedAt, releaseBody);
        if (this.isNewer(currentVersion, latestTag)) {
            return new UpdateCheckResult(UpdateCheckStatus.UPDATE_AVAILABLE, info, null);
        }
        return new UpdateCheckResult(UpdateCheckStatus.UP_TO_DATE, info, null);
    }

    private String matchFirst(final Pattern pattern, final String value) {
        final Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isNewer(final String currentVersion, final String latestVersion) {
        final List<Integer> currentParts = this.parseVersionParts(this.normalizeVersion(currentVersion));
        final List<Integer> latestParts = this.parseVersionParts(this.normalizeVersion(latestVersion));

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

    private String normalizeVersion(final String version) {
        if (version == null || version.isEmpty()) {
            return version;
        }
        if (version.startsWith("v") || version.startsWith("V")) {
            return version.substring(1);
        }
        return version;
    }

    private List<Integer> parseVersionParts(final String version) {
        final List<Integer> parts = new ArrayList<>();
        final Matcher matcher = NUMBER_PATTERN.matcher(version);
        while (matcher.find()) {
            parts.add(Integer.parseInt(matcher.group()));
        }
        return parts;
    }

    private String unescapeJsonString(final String value) {
        if (value == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                out.append(current);
                continue;
            }
            final char next = value.charAt(++i);
            switch (next) {
                case '"':
                case '\\':
                case '/':
                    out.append(next);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        final String hex = value.substring(i + 1, i + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (final NumberFormatException ignored) {
                            out.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(next);
                    break;
            }
        }
        return out.toString();
    }

    public enum UpdateCheckStatus {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        FAILED
    }

    public record UpdateCheckResult(UpdateCheckStatus status, UpdateInfo updateInfo, String errorMessage) {}

    public record UpdateInfo(
            String latestTag, String releaseUrl, String releaseName, String publishedAt, String releaseBody) {}
}
