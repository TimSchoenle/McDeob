package com.shanebeestudios.mcdeop.processor.mache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The intermediate files of a mache run, all under one scratch directory.
 *
 * @param bundleDirectory the unpacked mache archive
 * @param serverJar the server jar extracted from Mojang's bundler
 * @param remappedJar codebook's output
 * @param rawSourcesDirectory the decompiler's output, before patches
 * @param rejectsDirectory patch hunks that could not be applied
 * @param temporaryDirectory codebook's scratch directory
 * @param reportsDirectory where codebook writes reports, when a mache build asks for them
 * @param decompilerConfigFile the decompiler's classpath configuration
 * @param codebookLogFile codebook's complete output
 * @param decompilerLogFile the decompiler's complete output
 */
record MachePaths(
        Path bundleDirectory,
        Path serverJar,
        Path remappedJar,
        Path rawSourcesDirectory,
        Path rejectsDirectory,
        Path temporaryDirectory,
        Path reportsDirectory,
        Path decompilerConfigFile,
        Path codebookLogFile,
        Path decompilerLogFile) {

    static MachePaths create(final Path workDirectory) throws IOException {
        Files.createDirectories(workDirectory);
        return new MachePaths(
                workDirectory.resolve("bundle"),
                workDirectory.resolve("server.jar"),
                workDirectory.resolve("remapped.jar"),
                workDirectory.resolve("sources-raw"),
                workDirectory.resolve("patch-rejects"),
                workDirectory.resolve("tmp"),
                workDirectory.resolve("reports"),
                workDirectory.resolve("decompiler.cfg"),
                workDirectory.resolve("codebook.log"),
                workDirectory.resolve("decompiler.log"));
    }
}
