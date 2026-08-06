package com.shanebeestudios.mcdeop.processor;

import de.timmi6790.sourcerepair.CompileDiagnostic;
import de.timmi6790.sourcerepair.SourceRepairer;
import de.timmi6790.sourcerepair.SourceRepairer.RepairReport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns the decompiled sources into a tree that compiles.
 *
 * <p>Type-checking needs the same classpath the generated project builds against, which is the
 * downloaded libraries plus the annotation libraries Mojang compiles against but does not ship.
 * Those are only named in the generated build file, so they are fetched here as well; without them
 * every annotated declaration would be reported as an error and drown out the real defects.
 *
 * <p>The stage never fails the run. A version whose sources cannot be fully repaired still produces
 * output, and the errors that remain are logged so they can be looked at.
 */
@Slf4j
final class SourceRepairStage {

    private final ResourceRequest request;
    private final ProcessorPaths paths;
    private final ProcessorStatusReporter statusReporter;
    private final ProcessorDownloadService downloadService;

    /** How many unrepaired errors to log before summarising the rest. */
    private static final int REPORTED_ERRORS = 25;

    SourceRepairStage(
            final ResourceRequest request,
            final ProcessorPaths paths,
            final ProcessorStatusReporter statusReporter,
            final ProcessorDownloadService downloadService) {
        this.request = request;
        this.paths = paths;
        this.statusReporter = statusReporter;
        this.downloadService = downloadService;
    }

    /**
     * Repairs the decompiled sources in place.
     *
     * @throws IOException if the sources or the compile-time dependencies cannot be read or written
     */
    void run() throws IOException {
        this.statusReporter.send("Repairing decompiled sources...");

        final List<String> coordinates = new CompileDependencyResolver()
                .resolve(
                        this.paths.decompiledJarPath(),
                        this.paths.librariesPath(),
                        this.request.getVersion().releaseTime().toLocalDate())
                .coordinates();

        final List<Path> classpath = new ArrayList<>(this.downloadService.getDownloadedLibraryJars());
        classpath.addAll(this.downloadService.downloadCompileDependencies(coordinates));

        final int release = this.request.getJavaVersion().orElse(GradleProjectWriter.DEFAULT_GRADLE_JAVA_VERSION);
        final RepairReport report = new SourceRepairer().repair(this.paths.decompiledJarPath(), classpath, release);
        this.report(report);
    }

    private void report(final RepairReport report) {
        if (!report.attempted()) {
            this.statusReporter.send("Source repair skipped.");
            return;
        }

        if (report.clean()) {
            log.info(
                    "Decompiled sources compile cleanly ({} repair(s) over {} round(s))",
                    report.repairs(),
                    report.rounds());
            this.statusReporter.send("Decompiled sources compile cleanly.");
            return;
        }

        final List<CompileDiagnostic> remaining = report.remaining();
        log.warn(
                "{} repair(s) applied over {} round(s), but {} compile error(s) remain. The generated project"
                        + " will not build without manual fixes.",
                report.repairs(),
                report.rounds(),
                remaining.size());
        for (final CompileDiagnostic diagnostic : remaining.subList(0, Math.min(REPORTED_ERRORS, remaining.size()))) {
            log.warn("  {}:{}: {}", diagnostic.file().getFileName(), diagnostic.line(), diagnostic.summary());
        }
        if (remaining.size() > REPORTED_ERRORS) {
            log.warn("  ... and {} more", remaining.size() - REPORTED_ERRORS);
        }
        this.statusReporter.send(String.format("%d compile error(s) remain.", remaining.size()));
    }
}
