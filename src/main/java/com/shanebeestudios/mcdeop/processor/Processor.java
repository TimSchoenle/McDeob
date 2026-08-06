package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.Decompiler;
import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import com.shanebeestudios.mcdeop.processor.mache.MachePipeline;
import com.shanebeestudios.mcdeop.processor.mache.MacheUnavailableException;
import com.shanebeestudios.mcdeop.processor.remapper.ReconstructRemapper;
import com.shanebeestudios.mcdeop.processor.remapper.Remapper;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.Util;
import de.timmi6790.RequestModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
    private final OkHttpClient httpClient;

    private Processor(
            final ResourceRequest request,
            final ProcessorOptions options,
            @Nullable final ResponseConsumer responseConsumer) {
        this.request = request;
        this.options = options;

        this.remapper = new ReconstructRemapper();
        this.decompiler = Optional.ofNullable(this.options.decompilerType())
                .orElse(DecompilerType.VINEFLOWER)
                .createDecompiler();

        this.paths = ProcessorPaths.create(request);
        this.statusReporter = new ProcessorStatusReporter(responseConsumer);

        this.httpClient = RequestModule.createHttpClient();
        this.downloadService = new ProcessorDownloadService(request, this.httpClient, this.paths, this.statusReporter);
        this.gradleProjectWriter = new GradleProjectWriter(request, this.paths);
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
            Util.forceGC();
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
        // The mache pipeline always remaps, decompiles and extracts the server's libraries, so the options that
        // guard those steps in the standard pipeline cannot be unmet.
        if (this.options.isMache()) {
            return this.validateMacheOptions();
        }

        if (this.options.setupGradleProject() && !this.options.decompile()) {
            log.error("Gradle project setup requires decompile to be enabled.");
            this.statusReporter.send("Gradle setup requires decompile to be enabled.");
            return false;
        }

        if (this.options.setupGradleProject() && !this.options.downloadLibraries()) {
            log.error("Gradle project setup requires downloading libraries.");
            this.statusReporter.send("Gradle setup requires library downloads.");
            return false;
        }

        return true;
    }

    /**
     * Checks the request against what mache covers.
     *
     * @return {@code false} with the reason reported if the request cannot be served
     */
    private boolean validateMacheOptions() {
        if (this.request.type() != SourceType.SERVER) {
            log.error("The mache pipeline only supports server jars, because PaperMC only publishes server builds.");
            this.statusReporter.send("Mache only supports the server target.");
            return false;
        }

        if (!this.options.remap() || !this.options.decompile()) {
            log.info("The mache pipeline always remaps and decompiles; those options do not apply to it.");
        }
        if (this.options.downloadLibraries()) {
            log.info("The mache pipeline takes the server's libraries from the bundler, so no libraries are"
                    + " downloaded separately.");
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
        }
    }

    private void zipDecompiledOutput() throws IOException {
        log.info("Packing decompiled files into {}", this.paths.decompiledZipPath());
        this.statusReporter.send("Packing decompiled files ...");
        FileUtil.remove(this.paths.decompiledZipPath());
        FileUtil.zip(this.paths.decompiledJarPath(), this.paths.decompiledZipPath());
    }

    /**
     * Runs PaperMC's pipeline in place of the remap and decompile steps.
     *
     * @return {@code false} with the reason reported if mache cannot serve this version
     */
    private boolean runMachePipeline() throws IOException {
        final MachePipeline.Request pipelineRequest = new MachePipeline.Request(
                this.request.getVersion().id(),
                this.paths.jarPath(),
                this.paths.mappingsPath(),
                this.paths.decompiledJarPath(),
                this.paths.librariesPath(),
                this.paths.machePath(),
                this.paths.toolCachePath(),
                this.paths.javaRuntimePath(),
                this.options.javaHome());

        try {
            final MachePipeline.Result result =
                    new MachePipeline(this.httpClient, this.statusReporter::send).run(pipelineRequest);
            log.info(
                    "mache {} produced sources in {}",
                    result.bundle().artifact().version(),
                    this.paths.decompiledJarPath());
            return true;
        } catch (final MacheUnavailableException exception) {
            log.error(exception.getMessage());
            this.statusReporter.send(exception.getMessage());
            return false;
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

    /**
     * Runs McDeob's own remap and decompile steps.
     *
     * @return whether the decompiled sources directory was produced
     */
    private boolean runStandardPipeline() throws IOException {
        if (this.options.downloadLibraries()) {
            this.downloadService.downloadLibraries();
        }

        boolean remapped = false;
        if (this.options.remap() && this.downloadService.getMappingsUrl() != null) {
            this.remapJar();
            remapped = true;
        }

        final Path decompileJarPath =
                remapped ? this.paths.remappedJar() : this.prepareServerJarForDecompile(this.paths.jarPath(), false);

        if (this.options.remap() && !remapped) {
            this.reportUnmappedVersion(decompileJarPath);
        }

        if (!this.options.decompile()) {
            return false;
        }

        this.decompileJar(decompileJarPath);
        return true;
    }

    /**
     * Explains why a requested remap did not happen.
     *
     * <p>Mojang stopped publishing mappings once it stopped obfuscating the server, so a missing mappings file
     * usually means there is nothing to remap rather than that something went wrong. The jar itself is the only
     * reliable way to tell the two apart.
     *
     * @param jarPath the jar that would have been remapped
     */
    private void reportUnmappedVersion(final Path jarPath) throws IOException {
        final String version = this.request.getVersion().id();
        if (ObfuscationProbe.looksObfuscated(jarPath)) {
            log.warn(
                    "Mojang publishes no mappings for {}, and its jar is obfuscated, so readable names cannot be"
                            + " recovered for it.",
                    version);
            this.statusReporter.send(String.format("No mappings published for %s.", version));
            return;
        }

        log.info(
                "Mojang publishes no mappings for {} because its jar already ships readable names, so no remapping"
                        + " is needed.",
                version);
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

            java.util.concurrent.CompletableFuture.allOf(
                            this.downloadService.downloadJar(), this.downloadService.downloadMappings())
                    .join();

            final boolean producedSources;
            if (this.options.isMache()) {
                if (!this.runMachePipeline()) {
                    return false;
                }
                producedSources = true;
            } else {
                producedSources = this.runStandardPipeline();
            }

            if (producedSources && this.options.zipDecompileOutput()) {
                this.zipDecompiledOutput();
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
        if (this.remapper instanceof final Cleanup cleanup) {
            cleanup.cleanup();
        }
        if (this.decompiler instanceof final Cleanup cleanup) {
            cleanup.cleanup();
        }
    }
}
