package com.shanebeestudios.mcdeop.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;

/**
 * Fetches files and small documents over HTTP.
 *
 * <p>Kept separate from the Minecraft-specific download service because the PaperMC toolchain is resolved from
 * arbitrary Maven repositories, where a missing artifact is an expected outcome rather than a failure.
 */
@Slf4j
public class HttpDownloader {
    /** Anything larger is a build artifact, not metadata, and belongs on disk instead of in memory. */
    private static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;

    private final OkHttpClient httpClient;

    public HttpDownloader(final OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Reads a small document into memory.
     *
     * @param url document to read
     * @return the body, or empty if the server answered {@code 404}
     * @throws IOException on any other failure, or if the body exceeds {@value #MAX_DOCUMENT_BYTES} bytes
     */
    public Optional<byte[]> readDocument(final String url) throws IOException {
        final Request request = new Request.Builder().url(url).build();
        try (final Response response = this.httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful()) {
                throw new IOException("Request to " + url + " failed with HTTP " + response.code());
            }

            final ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Request to " + url + " returned no body");
            }
            if (body.contentLength() > MAX_DOCUMENT_BYTES) {
                throw new IOException("Document at " + url + " is too large: " + body.contentLength() + " bytes");
            }
            return Optional.of(body.bytes());
        }
    }

    /**
     * Downloads a file unless it is already present.
     *
     * <p>Presence alone is trusted because every caller addresses artifacts by an immutable, fully resolved
     * version, so a file that exists cannot be a different revision of the same URL.
     *
     * @param url file to download
     * @param target destination; parent directories are created
     * @return {@code true} if the file was downloaded, {@code false} if the cached copy was kept
     * @throws FileNotFoundException if the server answered {@code 404}
     */
    public boolean downloadIfAbsent(final String url, final Path target) throws IOException {
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            log.debug("Reusing cached {}", target.getFileName());
            return false;
        }

        this.download(url, target);
        return true;
    }

    /**
     * Downloads a file, replacing any existing copy.
     *
     * <p>The body is streamed to a temporary sibling and moved into place, so an interrupted download can never
     * leave a truncated file that a later run would mistake for a complete one.
     *
     * @param url file to download
     * @param target destination; parent directories are created
     * @throws FileNotFoundException if the server answered {@code 404}
     */
    public void download(final String url, final Path target) throws IOException {
        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final Request request = new Request.Builder().url(url).build();
        try (final Response response = this.httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new FileNotFoundException("Not found: " + url);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Download of " + url + " failed with HTTP " + response.code());
            }

            final ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Download of " + url + " returned no body");
            }

            final Path temporary = target.resolveSibling(target.getFileName() + ".part");
            try {
                try (final BufferedSink sink = Okio.buffer(Okio.sink(temporary))) {
                    sink.writeAll(body.source());
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
