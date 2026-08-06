package com.shanebeestudios.mcdeop.processor.mache;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.match.FuzzyLineMatcher;
import codechicken.diffpatch.util.LogLevel;
import codechicken.diffpatch.util.PatchMode;
import com.shanebeestudios.mcdeop.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies mache's patches to the decompiled sources.
 *
 * <p>These patches are what makes mache's output compilable: they repair the constructs the decompiler cannot
 * express as valid Java. The same library and matching mode as PaperMC's build are used, so a patch that applies
 * for Paper applies here.
 */
@Slf4j
final class MachePatchApplier {
    /**
     * Tolerates patches landing at a shifted line number, as sculptor does.
     *
     * <p>Exact matching would reject a whole file over an unrelated insertion earlier in it.
     */
    private static final PatchMode PATCH_MODE = PatchMode.OFFSET;

    /**
     * Applies every patch in a directory.
     *
     * @param patchesDirectory mache's {@code patches} directory
     * @param sourcesDirectory the decompiled sources to patch
     * @param outputDirectory where the patched sources are written; replaced if it already exists
     * @param rejectsDirectory where rejected hunks are written for inspection
     * @return a summary of what was applied
     * @throws IOException if the patch operation itself fails
     */
    Result apply(
            final Path patchesDirectory,
            final Path sourcesDirectory,
            final Path outputDirectory,
            final Path rejectsDirectory)
            throws IOException {
        FileUtil.remove(outputDirectory);
        FileUtil.remove(rejectsDirectory);
        Files.createDirectories(outputDirectory);
        Files.createDirectories(rejectsDirectory);

        final CliOperation.Result<PatchOperation.PatchesSummary> result;
        try (final PrintStream patchLog = new PrintStream(new Slf4jOutputStream(), true, StandardCharsets.UTF_8)) {
            result = PatchOperation.builder()
                    .logTo(patchLog)
                    .level(LogLevel.WARN)
                    .basePath(sourcesDirectory)
                    .patchesPath(patchesDirectory)
                    .outputPath(outputDirectory)
                    .rejectsPath(rejectsDirectory)
                    .mode(PATCH_MODE)
                    .minFuzz(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE)
                    .summary(false)
                    .build()
                    .operate();
        }

        final PatchOperation.PatchesSummary summary = result.summary;
        return new Result(summary.changedFiles, summary.failedMatches, rejectsDirectory);
    }

    /**
     * The outcome of a patch run.
     *
     * @param changedFiles files a patch was applied to
     * @param failedMatches hunks that could not be placed
     * @param rejectsDirectory where the failed hunks were written
     */
    record Result(int changedFiles, int failedMatches, Path rejectsDirectory) {
        boolean fullyApplied() {
            return this.failedMatches == 0;
        }
    }

    /** Forwards DiffPatch's line-oriented output into McDeob's log. */
    private static final class Slf4jOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(final int byteValue) {
            if (byteValue == '\n') {
                this.flushLine();
            } else if (byteValue != '\r') {
                this.buffer.write(byteValue);
            }
        }

        @Override
        public void close() {
            this.flushLine();
        }

        private void flushLine() {
            if (this.buffer.size() == 0) {
                return;
            }

            log.info("[patches] {}", this.buffer.toString(StandardCharsets.UTF_8));
            this.buffer.reset();
        }
    }
}
