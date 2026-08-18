package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.Decompiler;
import com.shanebeestudios.mcdeop.processor.remapper.ReconstructRemapper;
import com.shanebeestudios.mcdeop.processor.remapper.Remapper;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.Util;
import de.timmi6790.RequestModule;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class Processor {
    private final ResourceRequest request;
    private final ProcessorOptions options;
    private final Remapper remapper;
    private final Decompiler decompiler;

    private final ProcessorPaths paths;
    private final ProcessorStatusReporter statusReporter;
    private final ProcessorDownloadService downloadService;
    private final GradleProjectWriter gradleProjectWriter;

    private Processor(
            final ResourceRequest request,
            final ProcessorOptions options,
            @Nullable final ResponseConsumer responseConsumer) {
        this.request = request;
        this.options = options;

        this.remapper = new ReconstructRemapper();
        this.decompiler = options.decompilerTypeOrDefault().createDecompiler();

        this.paths = ProcessorPaths.create(request);
        this.statusReporter = new ProcessorStatusReporter(responseConsumer);

        final OkHttpClient httpClient = RequestModule.createHttpClient();
        this.downloadService = new ProcessorDownloadService(request, httpClient, this.paths, this.statusReporter);
        this.gradleProjectWriter = new GradleProjectWriter(request, this.paths);
    }

    /**
     * Resolves the directory a run for this target and version reads from and writes to.
     *
     * <p>Exposed so callers can point the user at the output without having to reproduce the layout.
     *
     * @param type the target being processed
     * @param version the Minecraft version being processed
     * @return the absolute output directory, which need not exist yet
     */
    public static Path resolveOutputDirectory(final SourceType type, final Version version) {
        return ProcessorPaths.resolveDataFolder(type, version).toAbsolutePath();
    }

    public static boolean runProcessor(
            final ResourceRequest request,
            final ProcessorOptions options,
            @Nullable final ResponseConsumer responseConsumer) {
        try {
            final Processor processor = new Processor(request, options, responseConsumer);
            final boolean success = processor.init();
            processor.cleanup();
            return success;
        } catch (final Exception e) {
            log.error("Failed to run processor", e);
            return false;
        } finally {
            Util.suggestGC();
        }
    }

    private boolean isValid() {
        if (this.downloadService.getJarUrl() == null) {
            log.error(
                    "Failed to find JAR URL for version {}-{}",
                    this.request.type(),
                    this.request.getVersion().id());
            this.statusReporter.send(String.format(
                    "Failed to find JAR URL for version %s-%s",
                    this.request.type(), this.request.getVersion().id()));
            return false;
        }
        return true;
    }

    private boolean validateOptions() {
        final Optional<String> conflict = this.options.validate();
        if (conflict.isPresent()) {
            log.error(conflict.get());
            this.statusReporter.send(conflict.get());
            return false;
        }
        return true;
    }

    private void remapJar() {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Remapping completed in {}!", duration))) {
            this.statusReporter.send("Remapping...");

            if (!Files.exists(this.paths.remappedJar())) {
                log.info("Remapping {} file...", this.paths.jarPath().getFileName());
                this.remapper.remap(this.paths.jarPath(), this.paths.mappingsPath(), this.paths.remappedJar());
            } else {
                log.info(
                        "{} already remapped... skipping mapping.",
                        this.paths.remappedJar().getFileName());
            }
        }
    }

    private void decompileJar(final Path jarPath) throws IOException {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Decompiling completed in {}!", duration))) {
            log.info("Decompiling final JAR file.");
            this.statusReporter.send("Decompiling... This will take a while!");

            FileUtil.remove(this.paths.decompiledJarPath());
            Files.createDirectories(this.paths.decompiledJarPath());

            this.decompiler.decompile(
                    jarPath,
                    this.paths.decompiledJarPath(),
                    this.downloadService.resolveDecompilerLibraries(this.options, this.decompiler));

            if (this.options.zipDecompileOutput()) {
                log.info("Packing decompiled files into {}", this.paths.decompiledZipPath());
                this.statusReporter.send("Packing decompiled files ...");
                FileUtil.remove(this.paths.decompiledZipPath());
                FileUtil.zip(this.paths.decompiledJarPath(), this.paths.decompiledZipPath());
            }
        }
    }

    private void setupGradleProject() throws IOException {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Gradle project setup completed in {}!", duration))) {
            this.statusReporter.send("Setting up Gradle project...");
            this.gradleProjectWriter.setupGradleProject();
        }
    }

    private Path prepareServerJarForDecompile(final Path jarPath, final boolean remapped) throws IOException {
        if (this.request.type() != SourceType.SERVER || remapped || this.downloadService.getMappingsUrl() != null) {
            return jarPath;
        }

        final String version = this.request.getVersion().id();
        final String nestedServerPath = String.format("META-INF/versions/%s/server-%s.jar", version, version);
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            final ZipEntry nestedServerJar = zipFile.getEntry(nestedServerPath);
            if (nestedServerJar == null) {
                log.info(
                        "No nested server jar found at {} in {}, continuing with downloaded jar.",
                        nestedServerPath,
                        jarPath.getFileName());
                return jarPath;
            }

            this.statusReporter.send("Preparing server JAR...");
            log.info("Extracting nested server jar {} from {}.", nestedServerPath, jarPath.getFileName());
            FileUtil.remove(this.paths.extractedServerJar());
            try (InputStream inputStream = zipFile.getInputStream(nestedServerJar)) {
                Files.copy(inputStream, this.paths.extractedServerJar());
            }
            return this.paths.extractedServerJar();
        }
    }

    public boolean init() {
        if (!this.isValid() || !this.validateOptions()) {
            return false;
        }

        try (final DurationTracker ignored = new DurationTracker(duration -> {
            log.info("Completed in {}!", duration);
            this.statusReporter.send(String.format("Completed in %s!", duration));
        })) {
            if (this.downloadService.getMappingsUrl() != null) {
                this.statusReporter.send("Downloading JAR & MAPPINGS...");
            } else {
                this.statusReporter.send("Downloading JAR...");
            }

            CompletableFuture.allOf(this.downloadService.downloadJar(), this.downloadService.downloadMappings())
                    .join();

            if (this.options.downloadLibraries()) {
                this.downloadService.downloadLibraries();
            }

            boolean remapped = false;
            if (this.options.remap()) {
                if (this.downloadService.getMappingsUrl() != null) {
                    this.remapJar();
                    remapped = true;
                } else {
                    log.warn(
                            "Remapping requested but no mappings found for version {}",
                            this.request.getVersion().id());
                }
            }

            if (this.options.decompile()) {
                Path decompileJarPath = remapped ? this.paths.remappedJar() : this.paths.jarPath();
                decompileJarPath = this.prepareServerJarForDecompile(decompileJarPath, remapped);
                this.decompileJar(decompileJarPath);
            }

            if (this.options.setupGradleProject()) {
                this.setupGradleProject();
            }
            return true;
        } catch (final IOException e) {
            log.error("Failed to run Processor!", e);
            return false;
        }
    }

    public void cleanup() {
        this.remapper.cleanup();
        this.decompiler.cleanup();
    }
}
