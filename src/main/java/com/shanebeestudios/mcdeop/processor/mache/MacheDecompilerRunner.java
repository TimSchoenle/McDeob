package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.processor.toolchain.JavaProcessRunner;
import com.shanebeestudios.mcdeop.util.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the decompiler version mache pins, with mache's own options.
 *
 * <p>McDeob bundles Vineflower already, but mache's patches were generated against one specific Vineflower
 * release and option set. Any difference in either changes the generated source, and every patch that touches a
 * changed line then fails, so the pinned distribution is downloaded and run instead of the bundled one.
 */
@Slf4j
final class MacheDecompilerRunner {
    private static final String TOOL_NAME = "decompiler";
    private static final String CONSOLE_DECOMPILER_MAIN =
            "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler";

    /** Matches sculptor's default; decompiling the whole server needs a large heap. */
    private static final String MAX_HEAP = "4G";

    private final JavaProcessRunner processRunner;

    MacheDecompilerRunner(final JavaProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Decompiles a jar into a source tree.
     *
     * @param arguments {@link MacheMeta#decompilerArguments()}
     * @param decompilerJar the pinned decompiler distribution
     * @param inputJar the remapped jar to decompile
     * @param classpath the server's libraries, passed through a configuration file
     * @param outputDirectory directory the sources are written to; replaced if it already exists
     * @param configFile where the classpath configuration is written
     * @param logFile file the decompiler's output is written to
     * @throws IOException if the decompiler fails or produces nothing
     */
    void run(
            final List<String> arguments,
            final Path decompilerJar,
            final Path inputJar,
            final List<Path> classpath,
            final Path outputDirectory,
            final Path configFile,
            final Path logFile)
            throws IOException {
        if (arguments.isEmpty()) {
            throw new IOException("mache.json declares no decompiler arguments");
        }

        this.writeClasspathConfig(classpath, configFile);

        FileUtil.remove(outputDirectory);
        Files.createDirectories(outputDirectory);

        final List<String> commandArguments = new ArrayList<>(arguments.size() + 4);
        commandArguments.addAll(arguments);
        commandArguments.add("-cfg");
        commandArguments.add(configFile.toAbsolutePath().toString());
        commandArguments.add(inputJar.toAbsolutePath().toString());
        commandArguments.add(outputDirectory.toAbsolutePath().toString());

        this.processRunner.run(
                TOOL_NAME,
                List.of(decompilerJar),
                CONSOLE_DECOMPILER_MAIN,
                commandArguments,
                MAX_HEAP,
                outputDirectory,
                logFile);
    }

    /**
     * Writes the library classpath as a decompiler configuration file.
     *
     * <p>A file is used rather than repeated command line options because the server's library list is long
     * enough to risk the platform's command line length limit.
     */
    private void writeClasspathConfig(final List<Path> classpath, final Path configFile) throws IOException {
        final StringBuilder builder = new StringBuilder();
        for (final Path entry : classpath) {
            builder.append("--add-external=").append(entry.toAbsolutePath()).append(System.lineSeparator());
        }

        final Path parent = configFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(configFile, builder.toString(), StandardCharsets.UTF_8);
        log.debug("Wrote {} decompiler classpath entries to {}", classpath.size(), configFile);
    }
}
