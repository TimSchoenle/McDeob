package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.Decompiler;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import de.timmi6790.launchermeta.data.release.LibraryArtifact;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.jetbrains.annotations.Nullable;

@Slf4j
final class ProcessorDownloadService {

    /** Where the annotation libraries Mojang compiles against are published. */
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

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
        return this.request.getJar().orElse(null);
    }

    @Nullable URL getMappingsUrl() {
        return this.request.getMappings().orElse(null);
    }

    CompletableFuture<Void> downloadJar() {
        final URL jarUrl = this.request
                .getJar()
                .orElseThrow(() -> new IllegalStateException("Jar URL should be validated before download."));
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.downloadFile(jarUrl, this.paths.jarPath(), "JAR");
            } catch (final IOException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    CompletableFuture<Void> downloadMappings() {
        final URL mappingsUrl = this.getMappingsUrl();
        if (mappingsUrl == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                this.downloadFile(mappingsUrl, this.paths.mappingsPath(), "mappings");
            } catch (final IOException exception) {
                throw new CompletionException(exception);
            }
            return null;
        });
    }

    void downloadLibraries() throws IOException {
        final List<LibraryArtifact> libraries = this.request.getLibraries().stream()
                .filter(library -> library.path() != null && library.url() != null)
                .toList();

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
                this.downloadFile(library.url(), outputPath, "library");

                final int downloaded = i + 1;
                if (downloaded == libraries.size() || downloaded % 25 == 0) {
                    this.statusReporter.send(
                            String.format("Downloading libraries... (%d/%d)", downloaded, libraries.size()));
                }
            }
        }
    }

    /**
     * Fetches the compile-time dependencies the decompiled sources need.
     *
     * <p>These are the annotation libraries Mojang compiles against without shipping them, so they
     * appear in no manifest and have to come from Maven Central. A download that fails is skipped
     * rather than fatal: the sources still compile apart from their annotated declarations, and a
     * partial type-check is more useful than none.
     *
     * @param coordinates Maven coordinates in {@code group:artifact:version} form
     * @return the jars that could be fetched
     */
    List<Path> downloadCompileDependencies(final List<String> coordinates) {
        final List<Path> jars = new ArrayList<>(coordinates.size());
        for (final String coordinate : coordinates) {
            final String[] parts = coordinate.split(":");
            if (parts.length != 3) {
                log.warn("Ignoring malformed compile-time dependency coordinate '{}'", coordinate);
                continue;
            }

            final String fileName = parts[1] + "-" + parts[2] + ".jar";
            final Path target = this.paths.compileLibrariesPath().resolve(fileName);
            final String url = String.format(
                    "%s%s/%s/%s/%s", MAVEN_CENTRAL, parts[0].replace('.', '/'), parts[1], parts[2], fileName);
            try {
                this.downloadFile(URI.create(url).toURL(), target, "compile-time dependency");
                jars.add(target);
            } catch (final IOException exception) {
                log.warn("Failed to download compile-time dependency {}; continuing without it", coordinate, exception);
            }
        }
        return jars;
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

    List<Path> getDownloadedLibraryJars() throws IOException {
        if (!Files.isDirectory(this.paths.librariesPath())) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(this.paths.librariesPath())) {
            return files.filter(Files::isRegularFile)
                    .filter(this::isLibraryJar)
                    .sorted()
                    .toList();
        }
    }

    private boolean isLibraryJar(final Path path) {
        final String fileName = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
        return fileName.endsWith(".jar") && !fileName.contains("-natives-");
    }

    private void downloadFile(final URL url, final Path path, final String fileType) throws IOException {
        try (final DurationTracker ignored = new DurationTracker(
                duration -> log.info("Successfully downloaded {} file in {}!", fileType, duration))) {
            log.info("Downloading {} file...", fileType);
            final Request httpRequest = new Request.Builder().url(url).build();

            try (final Response response = this.httpClient.newCall(httpRequest).execute()) {
                if (response.body() == null) {
                    throw new IOException("Response body was null");
                }

                final long length = response.body().contentLength();
                if (Files.exists(path) && Files.size(path) == length) {
                    log.info("Already have {}, skipping download.", path.getFileName());
                    return;
                }

                FileUtil.remove(path);
                final Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (BufferedSink sink = Okio.buffer(Okio.sink(path))) {
                    sink.writeAll(response.body().source());
                }
            }
        }
    }
}
