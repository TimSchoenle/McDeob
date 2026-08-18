package com.shanebeestudios.mcdeop.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.timmi6790.RequestModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class GithubReleaseChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + GeneratedConstant.GITHUB_REPO_NAME + "/releases/latest";

    /** Repository name used when the build could not determine one; the API call would 404. */
    private static final String UNKNOWN_REPOSITORY = "unknown/unknown";

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public GithubReleaseChecker() {
        this(RequestModule.createHttpClient());
    }

    public GithubReleaseChecker(final OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Algorithm:
     * 1) Fetch latest GitHub release JSON.
     * 2) Read {@code tag_name} and release URL.
     * 3) Parse all numeric groups from current and latest versions.
     * 4) Compare each numeric part in order (missing parts treated as 0).
     * 5) Return update info only when latest > current.
     */
    public Optional<UpdateInfo> checkForUpdate(final String currentVersion) throws IOException {
        final UpdateCheckResult result = this.checkForUpdateDetailed(currentVersion);
        return result.status() == UpdateCheckStatus.UPDATE_AVAILABLE
                ? Optional.ofNullable(result.updateInfo())
                : Optional.empty();
    }

    public UpdateCheckResult checkForUpdateDetailed(final String currentVersion) throws IOException {
        if (GeneratedConstant.GITHUB_REPO_NAME.equals(UNKNOWN_REPOSITORY)) {
            return new UpdateCheckResult(
                    UpdateCheckStatus.FAILED, null, "This build does not know which repository it came from.");
        }

        final Request request = new Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        final JsonNode root;
        try (final Response response = this.httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new UpdateCheckResult(
                        UpdateCheckStatus.FAILED, null, "GitHub API returned HTTP " + response.code());
            }

            final ResponseBody body = response.body();
            if (body == null) {
                return new UpdateCheckResult(UpdateCheckStatus.FAILED, null, "GitHub API returned an empty response");
            }
            root = this.objectMapper.readTree(body.byteStream());
        }

        final String latestTag = text(root, "tag_name");
        if (latestTag == null || latestTag.isBlank()) {
            return new UpdateCheckResult(UpdateCheckStatus.FAILED, null, "Could not parse release tag from response");
        }

        final UpdateInfo info = new UpdateInfo(
                latestTag, text(root, "html_url"), text(root, "name"), text(root, "published_at"), text(root, "body"));

        return isNewer(currentVersion, latestTag)
                ? new UpdateCheckResult(UpdateCheckStatus.UPDATE_AVAILABLE, info, null)
                : new UpdateCheckResult(UpdateCheckStatus.UP_TO_DATE, info, null);
    }

    /**
     * Reads a top-level string field.
     *
     * <p>Replaces the hand-written regex extraction this used to do, which depended on GitHub's field
     * ordering to avoid matching a nested {@code name} from the assets array, and needed its own JSON
     * string unescaper for the release body.
     *
     * @param root the parsed response
     * @param field field to read
     * @return the field value, or {@code null} when absent or not a string
     */
    @Nullable private static String text(final JsonNode root, final String field) {
        final JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Compares two version strings by their numeric parts.
     *
     * <p>Package-private rather than private so the ordering rules can be tested without a network
     * round trip.
     *
     * @param currentVersion the running version
     * @param latestVersion the version advertised by the release
     * @return {@code true} when the latest version is strictly newer
     */
    static boolean isNewer(final String currentVersion, final String latestVersion) {
        final List<Integer> currentParts = parseVersionParts(normalizeVersion(currentVersion));
        final List<Integer> latestParts = parseVersionParts(normalizeVersion(latestVersion));

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

    private static String normalizeVersion(@Nullable final String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        if (version.startsWith("v") || version.startsWith("V")) {
            return version.substring(1);
        }
        return version;
    }

    private static List<Integer> parseVersionParts(final String version) {
        final List<Integer> parts = new ArrayList<>();
        final Matcher matcher = NUMBER_PATTERN.matcher(version);
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (final NumberFormatException exception) {
                // A run of digits too long for an int cannot be a meaningful version part.
                log.debug("Ignoring oversized version part '{}' in '{}'", matcher.group(), version);
            }
        }
        return parts;
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
