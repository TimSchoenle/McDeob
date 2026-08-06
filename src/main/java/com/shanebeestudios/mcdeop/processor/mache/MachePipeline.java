package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.processor.toolchain.JavaProcessRunner;
import com.shanebeestudios.mcdeop.processor.toolchain.JavaRuntime;
import com.shanebeestudios.mcdeop.processor.toolchain.JavaRuntimeProvider;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.HttpDownloader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

/**
 * Runs PaperMC's deobfuscation pipeline for one Minecraft version.
 *
 * <p>Every tool and argument comes from the mache build's own metadata rather than from McDeob's defaults, which
 * is what makes the output match Paper's: codebook (with unpick definitions) for remapping, the pinned decompiler
 * release for decompilation, and mache's patches to make the result compile.
 */
@Slf4j
public final class MachePipeline {
    private final HttpDownloader downloader;
    private final Consumer<String> statusReporter;

    /**
     * @param httpClient client used for every download this pipeline performs
     * @param statusReporter receives short progress messages for the user interface
     */
    public MachePipeline(final OkHttpClient httpClient, final Consumer<String> statusReporter) {
        this.downloader = new HttpDownloader(httpClient);
        this.statusReporter = statusReporter;
    }

    /**
     * Produces patched, compilable sources for a Minecraft server version.
     *
     * @param request the inputs and output locations
     * @return what the run produced
     * @throws MacheUnavailableException if PaperMC publishes no mache build for the version
     * @throws IOException if any stage fails
     */
    public Result run(final Request request) throws IOException {
        final MachePaths paths = MachePaths.create(request.workDirectory());

        this.statusReporter.accept("Resolving PaperMC mache...");
        final MacheBundle bundle = new MacheBundleService(this.downloader, request.toolCacheDirectory())
                .resolve(request.minecraftVersion(), paths.bundleDirectory());
        final MacheMeta meta = bundle.meta();

        this.statusReporter.accept("Extracting the server jar and libraries...");
        final ServerBundle serverBundle =
                ServerBundle.extract(request.bundlerJar(), paths.serverJar(), request.librariesDirectory());

        final JavaRuntime runtime = new JavaRuntimeProvider(
                        this.downloader, request.runtimeDirectory(), request.javaHome())
                .resolve(this.statusReporter);
        final JavaProcessRunner processRunner = new JavaProcessRunner(runtime);

        final MavenArtifactResolver resolver =
                new MavenArtifactResolver(this.downloader, meta.repositories(), request.toolCacheDirectory());

        this.statusReporter.accept("Downloading the PaperMC toolchain...");
        final Path codebookJar = resolver.resolve(meta.codebook());
        final Path decompilerJar = resolver.resolve(meta.decompiler());
        final Path remapperJar = resolveOptional(resolver, meta.remapper());
        final Path paramMappings = resolveOptional(resolver, meta.paramMappings());
        final Path constants = resolveOptional(resolver, meta.constants());
        this.logToolchain(meta, constants);

        this.remap(
                meta,
                processRunner,
                new CodebookRunner.Inputs(
                        codebookJar,
                        remapperJar,
                        existingOrNull(request.mojangMappings()),
                        paramMappings,
                        constants,
                        serverBundle.serverJar(),
                        serverBundle.libraries(),
                        paths.remappedJar(),
                        paths.temporaryDirectory(),
                        paths.reportsDirectory()),
                paths.codebookLogFile());

        this.decompile(meta, processRunner, decompilerJar, serverBundle.libraries(), paths);

        return this.patch(bundle, paths, request.outputSourcesDirectory());
    }

    private void remap(
            final MacheMeta meta,
            final JavaProcessRunner processRunner,
            final CodebookRunner.Inputs inputs,
            final Path logFile)
            throws IOException {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Remapping completed in {}!", duration))) {
            this.statusReporter.accept("Remapping with codebook... This will take a while!");
            new CodebookRunner(processRunner).run(meta.codebookArguments(), inputs, logFile);
        }
    }

    private void decompile(
            final MacheMeta meta,
            final JavaProcessRunner processRunner,
            final Path decompilerJar,
            final List<Path> libraries,
            final MachePaths paths)
            throws IOException {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Decompiling completed in {}!", duration))) {
            this.statusReporter.accept("Decompiling... This will take a while!");
            new MacheDecompilerRunner(processRunner)
                    .run(
                            meta.decompilerArguments(),
                            decompilerJar,
                            paths.remappedJar(),
                            libraries,
                            paths.rawSourcesDirectory(),
                            paths.decompilerConfigFile(),
                            paths.decompilerLogFile());
        }
    }

    /**
     * Applies mache's patches, or promotes the unpatched sources when the build ships none.
     *
     * <p>A failed patch is reported rather than fatal: the sources are still usable, they just will not compile
     * everywhere, and the rejected hunks are kept so the user can see what was missed.
     */
    private Result patch(final MacheBundle bundle, final MachePaths paths, final Path outputSourcesDirectory)
            throws IOException {
        final Path patchesDirectory = bundle.patchesDirectory();
        if (patchesDirectory == null) {
            log.info(
                    "mache {} ships no patches, using the decompiled sources as they are.",
                    bundle.artifact().version());
            FileUtil.remove(outputSourcesDirectory);
            final Path parent = outputSourcesDirectory.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(paths.rawSourcesDirectory(), outputSourcesDirectory);
            return new Result(bundle, 0, 0, null);
        }

        this.statusReporter.accept("Applying mache patches...");
        final MachePatchApplier.Result result = new MachePatchApplier()
                .apply(patchesDirectory, paths.rawSourcesDirectory(), outputSourcesDirectory, paths.rejectsDirectory());

        if (result.fullyApplied()) {
            log.info("Applied {} mache patches.", result.changedFiles());
            return new Result(bundle, result.changedFiles(), 0, null);
        }

        log.error(
                "{} mache patch hunks could not be applied; the sources may not compile. Rejected hunks: {}",
                result.failedMatches(),
                result.rejectsDirectory());
        this.statusReporter.accept(
                String.format("Applied patches with %d failed hunks, see the log.", result.failedMatches()));
        return new Result(bundle, result.changedFiles(), result.failedMatches(), result.rejectsDirectory());
    }

    private void logToolchain(final MacheMeta meta, @Nullable final Path constants) {
        log.info(
                "mache {} pins codebook {} and {} {}.",
                meta.macheVersion(),
                meta.codebook().version(),
                meta.decompiler().name(),
                meta.decompiler().version());
        if (constants == null) {
            log.info("This mache build declares no unpick constants, so constants are left inlined.");
        } else {
            log.info("Unpicking constants using {}.", meta.constants().coordinates());
        }
    }

    private static @Nullable Path resolveOptional(
            final MavenArtifactResolver resolver, @Nullable final MavenArtifact artifact) throws IOException {
        return artifact == null ? null : resolver.resolve(artifact);
    }

    private static @Nullable Path existingOrNull(final Path path) {
        return Files.isRegularFile(path) ? path : null;
    }

    /**
     * The inputs of a pipeline run.
     *
     * @param minecraftVersion the Minecraft version id, as Mojang names it
     * @param bundlerJar Mojang's downloaded server jar
     * @param mojangMappings Mojang's ProGuard mappings; ignored if the file does not exist
     * @param outputSourcesDirectory where the finished sources are written
     * @param librariesDirectory where the server's bundled libraries are extracted
     * @param workDirectory scratch directory for this Minecraft version
     * @param toolCacheDirectory Maven-layout tool cache, shared across versions
     * @param runtimeDirectory directory for a downloaded Java runtime, shared across versions
     * @param javaHome a Java home chosen by the user, or {@code null} to detect one
     */
    public record Request(
            String minecraftVersion,
            Path bundlerJar,
            Path mojangMappings,
            Path outputSourcesDirectory,
            Path librariesDirectory,
            Path workDirectory,
            Path toolCacheDirectory,
            Path runtimeDirectory,
            @Nullable Path javaHome) {}

    /**
     * What a pipeline run produced.
     *
     * @param bundle the mache build that was used
     * @param patchedFiles number of files a patch was applied to
     * @param failedPatches number of patch hunks that could not be applied
     * @param rejectsDirectory where rejected hunks were written, or {@code null} if there were none
     */
    public record Result(
            MacheBundle bundle,
            int patchedFiles,
            int failedPatches,
            @Nullable Path rejectsDirectory) {
        public boolean fullyPatched() {
            return this.failedPatches == 0;
        }
    }
}
