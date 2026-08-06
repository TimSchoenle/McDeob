package com.shanebeestudios.mcdeop.processor.toolchain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a jar in a child JVM and captures its output.
 *
 * <p>The PaperMC tools have to run out of process: mache pins a different codebook and decompiler version for
 * every Minecraft version, codebook loads its remapper through a runtime class loader, and its logging stack
 * conflicts with McDeob's. A child JVM satisfies all three, and is what PaperMC's own build does.
 */
@Slf4j
public final class JavaProcessRunner {
    /** How much of a failing tool's output to repeat in McDeob's log, where the cause usually is. */
    private static final int RETAINED_LINES = 40;

    private final JavaRuntime runtime;

    public JavaProcessRunner(final JavaRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Runs a tool and waits for it to finish.
     *
     * <p>The tool's output goes to {@code logFile} rather than into McDeob's log, because the decompiler alone
     * emits thousands of lines about Minecraft's inner classes, which would bury McDeob's own progress. On failure
     * the tail of that output is repeated in McDeob's log so there is something to act on.
     *
     * @param toolName name used when reporting on this tool
     * @param classpath jars to put on the child JVM's class path
     * @param mainClass entry point, or {@code null} to use the {@code Main-Class} of the first classpath entry
     * @param arguments arguments passed to the entry point
     * @param maxHeap value for {@code -Xmx}, for example {@code 2G}
     * @param workingDirectory directory the child runs in
     * @param logFile file the tool's complete output is written to, replacing any earlier run's
     * @throws IOException if the tool cannot be started, or exits with a non-zero status
     */
    public void run(
            final String toolName,
            final List<Path> classpath,
            @Nullable final String mainClass,
            final List<String> arguments,
            final String maxHeap,
            final Path workingDirectory,
            final Path logFile)
            throws IOException {
        if (classpath.isEmpty()) {
            throw new IOException("Cannot run " + toolName + " with an empty class path");
        }

        final List<String> command = this.buildCommand(classpath, mainClass, arguments, maxHeap);
        log.debug("Running {}: {}", toolName, command);
        log.info("Running {}, full output in {}", toolName, logFile);

        final Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        final Deque<String> retainedOutput = new ArrayDeque<>(RETAINED_LINES);
        final Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (final BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                final BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();

                if (line.isBlank()) {
                    continue;
                }
                if (retainedOutput.size() == RETAINED_LINES) {
                    retainedOutput.removeFirst();
                }
                retainedOutput.addLast(line);
            }
        }

        final int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (final InterruptedException exception) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException(toolName + " was interrupted", exception);
        }

        if (exitCode != 0) {
            log.error("{} failed with exit code {}. Its last output was:", toolName, exitCode);
            for (final String line : retainedOutput) {
                log.error("[{}] {}", toolName, line);
            }
            log.error("The complete {} output is in {}", toolName, logFile);
            throw new IOException(toolName + " failed with exit code " + exitCode + ", see " + logFile);
        }
    }

    private List<String> buildCommand(
            final List<Path> classpath,
            @Nullable final String mainClass,
            final List<String> arguments,
            final String maxHeap)
            throws IOException {
        final List<String> command = new ArrayList<>(arguments.size() + 6);
        command.add(this.runtime.executable().toString());
        command.add("-Xmx" + maxHeap);
        // Without this the tools read and write Minecraft's non-ASCII string literals in the platform's default
        // charset, which corrupts them on Windows.
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(joinClasspath(classpath));
        command.add(mainClass != null ? mainClass : mainClassOf(classpath.get(0)));
        command.addAll(arguments);
        return command;
    }

    private static String joinClasspath(final List<Path> classpath) {
        final StringBuilder builder = new StringBuilder();
        for (final Path entry : classpath) {
            if (!builder.isEmpty()) {
                builder.append(File.pathSeparatorChar);
            }
            builder.append(entry.toAbsolutePath());
        }
        return builder.toString();
    }

    /**
     * Reads the {@code Main-Class} attribute of an executable jar.
     *
     * @param jar the jar to inspect
     * @return the declared main class
     * @throws IOException if the jar has no manifest or no {@code Main-Class}
     */
    private static String mainClassOf(final Path jar) throws IOException {
        try (final JarFile jarFile = new JarFile(jar.toFile())) {
            final Manifest manifest = jarFile.getManifest();
            final String mainClass =
                    manifest == null ? null : manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass == null || mainClass.isBlank()) {
                throw new IOException("Jar declares no Main-Class: " + jar);
            }
            return mainClass;
        }
    }
}
