package de.timmi6790.launchermeta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.timmi6790.launchermeta.data.release.DownloadInfo;
import de.timmi6790.launchermeta.data.release.Downloads;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import de.timmi6790.launchermeta.data.version.VersionManifest;
import de.timmi6790.launchermeta.data.version.VersionType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LauncherMeta {
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final URL versionManifestUrl;

    public LauncherMeta(final OkHttpClient httpClient) {
        try {
            this.versionManifestUrl = URI.create(VERSION_MANIFEST_URL).toURL();
        } catch (final MalformedURLException e) {
            throw new IllegalStateException("Failed to parse version manifest url", e);
        }
        this.httpClient = httpClient;
    }

    private JsonNode get(final URL url) throws IOException {
        final Request request = new Request.Builder().url(url).build();
        final Call call = this.httpClient.newCall(request);
        try (Response response = call.execute()) {
            final ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body was null");
            }
            return this.objectMapper.readTree(body.byteStream());
        }
    }

    public VersionManifest getVersionManifest() throws IOException {
        final JsonNode root = this.get(this.versionManifestUrl);
        final JsonNode latest = root.path("latest");
        final VersionManifest.Latest latestRecord = new VersionManifest.Latest(
                latest.path("release").asText(null), latest.path("snapshot").asText(null));

        final List<Version> versions = new ArrayList<>();
        for (final JsonNode item : root.path("versions")) {
            versions.add(this.mapVersion(item));
        }

        return new VersionManifest(latestRecord, versions);
    }

    public ReleaseManifest getReleaseManifest(final Version version) throws IOException {
        final JsonNode root = this.get(version.url());
        final Downloads downloads = this.mapDownloads(root.path("downloads"));
        return new ReleaseManifest(downloads, root.path("mainClass").asText(null), version);
    }

    private Version mapVersion(final JsonNode node) throws MalformedURLException {
        return new Version(
                node.path("id").asText(),
                this.parseVersionType(node.path("type").asText()),
                this.toUrl(node.path("url").asText()),
                OffsetDateTime.parse(node.path("releaseTime").asText()));
    }

    private VersionType parseVersionType(final String typeName) {
        final String normalized = typeName.replace('-', '_').toUpperCase();
        return VersionType.valueOf(normalized);
    }

    private Downloads mapDownloads(final JsonNode downloadsNode) throws MalformedURLException {
        return new Downloads(
                this.mapDownloadInfo(downloadsNode.path("client")),
                this.mapDownloadInfo(downloadsNode.path("client_mappings")),
                this.mapDownloadInfo(downloadsNode.path("server")),
                this.mapDownloadInfo(downloadsNode.path("server_mappings")));
    }

    private DownloadInfo mapDownloadInfo(final JsonNode node) throws MalformedURLException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new DownloadInfo(
                node.path("sha1").asText(null),
                node.path("size").asLong(0L),
                this.toUrl(node.path("url").asText()));
    }

    private URL toUrl(final String rawUrl) throws MalformedURLException {
        return URI.create(rawUrl).toURL();
    }
}
