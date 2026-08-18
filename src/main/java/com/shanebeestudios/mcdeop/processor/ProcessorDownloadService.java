package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.Decompiler;
import com.shanebeestudios.mcdeop.util.Checksums;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import de.timmi6790.launchermeta.data.release.DownloadInfo;
import de.timmi6790.launchermeta.data.release.LibraryArtifact;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import org.jetbrains.annotations.Nullable;

@Slf4j
final class ProcessorDownloadService {
    private final ResourceRequest request;
    private final OkHttpClient httpClient;
    private final ProcessorPaths paths;
    private final ProcessorStatusReporter statusReporter;

    ProcessorDownloadService(
            final ResourceRequest request,
            final OkHttpClient httpClient,
            final ProcessorPaths paths,
            final ProcessorStatusReporter statusReporter) {
        this.request = request;
        this.httpClient = httpClient;
        this.paths = paths;
        this.statusReporter = statusReporter;
    }

    @Nullable URL getJarUrl() {
        return this.request.getJar().map(DownloadInfo::url).orElse(null);
    }

    @Nullable URL getMappingsUrl() {
        return this.request.getMappings().map(DownloadInfo::url).orElse(null);
    }

    CompletableFuture<Void> downloadJar() {
        final DownloadInfo jar = this.request
                .getJar()
                .orElseThrow(() -> new IllegalStateException("Jar URL should be validated before download."));
        return this.downloadAsync(jar, this.paths.jarPath(), "JAR");
    }

    CompletableFuture<Void> downloadMappings() {
        return this.request
                .getMappings()
                .map(mappings -> this.downloadAsync(mappings, this.paths.mappingsPath(), "mappings"))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> downloadAsync(final DownloadInfo download, final Path path, final String fileType) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.downloadFile(download.url(), path, fileType, download.sha1(), download.size());
            } catch (final IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    void downloadLibraries() throws IOException {
        final List<LibraryArtifact> libraries = new ArrayList<>();
        for (final LibraryArtifact library : this.request.getLibraries()) {
            if (library.path() != null && library.url() != null) {
                libraries.add(library);
            }
        }

        if (libraries.isEmpty()) {
            log.warn("No downloadable library artifacts were found for this version.");
            this.statusReporter.send("No libraries found to download.");
            return;
        }

        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Library download completed in {}!", duration))) {
            this.statusReporter.send("Downloading libraries...");
            for (int i = 0; i < libraries.size(); i++) {
                final LibraryArtifact library = libraries.get(i);
                final Path outputPath = this.paths.librariesPath().resolve(library.path());
                this.downloadFile(library.url(), outputPath, "library", library.sha1(), library.size());

                final int downloaded = i + 1;
                if (downloaded == libraries.size() || downloaded % 25 == 0) {
                    this.statusReporter.send(
                            String.format("Downloading libraries... (%d/%d)", downloaded, libraries.size()));
                }
            }
        }
    }

    List<Path> resolveDecompilerLibraries(final ProcessorOptions options, final Decompiler decompiler)
            throws IOException {
        if (!options.downloadLibraries()) {
            return List.of();
        }
        if (!decompiler.supportsExternalLibraries()) {
            log.info(
                    "{} does not support external libraries; decompiling without downloaded dependencies.",
                    decompiler.getClass().getSimpleName());
            return List.of();
        }

        final List<Path> libraries = this.getDownloadedLibraryJars();
        if (!libraries.isEmpty()) {
            log.info(
                    "Passing {} downloaded libraries to {}.",
                    libraries.size(),
                    decompiler.getClass().getSimpleName());
        } else {
            log.warn(
                    "No downloaded library jars found to pass to {}.",
                    decompiler.getClass().getSimpleName());
        }
        return libraries;
    }

    private List<Path> getDownloadedLibraryJars() throws IOException {
        final List<Path> libraries = new ArrayList<>();
        if (!Files.isDirectory(this.paths.librariesPath())) {
            return libraries;
        }

        Files.walkFileTree(this.paths.librariesPath(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
                if (LibraryJars.isCompileVisible(file)) {
                    libraries.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Sorted so the classpath handed to the decompiler is stable between runs.
        Collections.sort(libraries);
        return libraries;
    }

    /**
     * Downloads a file unless a verified copy is already present.
     *
     * <p>The size and SHA-1 from the manifest are checked before any request is made, so an intact
     * file skips the network entirely, and again after writing, so a truncated or corrupted transfer
     * fails loudly rather than being handed to the remapper.
     *
     * @param url source to download from
     * @param path destination to write to
     * @param fileType label used in progress logging
     * @param expectedSha1 digest from the manifest, or {@code null} when not advertised
     * @param expectedSize size from the manifest, or a non-positive value when not advertised
     * @throws IOException if the transfer fails or the result does not match the manifest
     */
    private void downloadFile(
            final URL url,
            final Path path,
            final String fileType,
            @Nullable final String expectedSha1,
            final long expectedSize)
            throws IOException {
        if (Checksums.matches(path, expectedSha1, expectedSize)) {
            log.info("Already have a verified {}, skipping download.", path.getFileName());
            return;
        }

        try (final DurationTracker ignored = new DurationTracker(
                duration -> log.info("Successfully downloaded {} file in {}!", fileType, duration))) {
            log.info("Downloading {} file from Mojang...", fileType);
            final Request httpRequest = new Request.Builder().url(url).build();

            try (final Response response = this.httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Download of " + url + " failed with HTTP " + response.code());
                }

                final ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Response body was null");
                }

                FileUtil.remove(path);
                final Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (final BufferedSink sink = Okio.buffer(Okio.sink(path))) {
                    sink.writeAll(body.source());
                }
            }
        }

        this.verifyDownload(path, expectedSha1);
    }

    /**
     * Verifies a freshly written file against the manifest, deleting it if it does not match.
     *
     * <p>A mismatched file is removed rather than left in place, so the next run downloads it again
     * instead of treating the corrupt copy as a cache hit.
     *
     * @param path file that was just written
     * @param expectedSha1 digest from the manifest, or {@code null} when not advertised
     * @throws IOException if the file cannot be read or does not match
     */
    private void verifyDownload(final Path path, @Nullable final String expectedSha1) throws IOException {
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            log.debug("No SHA-1 advertised for {}, skipping verification.", path.getFileName());
            return;
        }

        final String actualSha1 = Checksums.sha1(path);
        if (actualSha1.equalsIgnoreCase(expectedSha1.trim())) {
            return;
        }

        FileUtil.remove(path);
        throw new IOException(String.format(
                "Checksum mismatch for %s: expected SHA-1 %s but got %s. The download was corrupted and has been"
                        + " discarded.",
                path.getFileName(), Checksums.describe(expectedSha1), actualSha1));
    }
}
