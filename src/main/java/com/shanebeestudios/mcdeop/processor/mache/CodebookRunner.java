package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.processor.toolchain.JavaProcessRunner;
import com.shanebeestudios.mcdeop.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Runs codebook with the command line mache pins.
 *
 * <p>The argument list is taken verbatim from {@code mache.json} and only its placeholders are filled in, so
 * McDeob does not have to know which codebook version expects which options. That matters because codebook 2
 * dropped the explicit remapper and mappings options that codebook 1 required.
 */
@Slf4j
final class CodebookRunner {
    private static final String TOOL_NAME = "codebook";

    /** Matches sculptor's default; codebook's jar inspection needs considerably more than a default heap. */
    private static final String MAX_HEAP = "2G";

    /**
     * Codebook parses {@code --input-classpath} on {@code :} regardless of platform, splitting only where the
     * colon is not followed by a backslash so Windows drive letters survive.
     */
    private static final char CODEBOOK_CLASSPATH_SEPARATOR = ':';

    private final JavaProcessRunner processRunner;

    CodebookRunner(final JavaProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Remaps a jar.
     *
     * @param argumentTemplates {@link MacheMeta#codebookArguments()}
     * @param inputs the files each placeholder resolves to
     * @param logFile file codebook's output is written to
     * @throws IOException if an argument references a file the mache build did not declare, or codebook fails
     */
    void run(final List<String> argumentTemplates, final Inputs inputs, final Path logFile) throws IOException {
        if (argumentTemplates.isEmpty()) {
            throw new IOException("mache.json declares no codebook arguments");
        }

        // codebook refuses to overwrite its output unless --force is passed, which mache's argument list does
        // not include, so the previous run's jar has to go first.
        FileUtil.remove(inputs.outputJar());
        Files.createDirectories(inputs.tempDirectory());

        this.processRunner.run(
                TOOL_NAME,
                List.of(inputs.codebookJar()),
                null,
                substitute(argumentTemplates, placeholders(inputs)),
                MAX_HEAP,
                inputs.tempDirectory(),
                logFile);

        if (!Files.isRegularFile(inputs.outputJar())) {
            throw new IOException("codebook did not produce " + inputs.outputJar());
        }
    }

    private static Map<String, String> placeholders(final Inputs inputs) {
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("tempDir", absolute(inputs.tempDirectory()));
        values.put("reportsDir", absolute(inputs.reportsDirectory()));
        values.put("input", absolute(inputs.inputJar()));
        values.put("output", absolute(inputs.outputJar()));
        values.put("inputClasspath", joinClasspath(inputs.classpathJars()));
        putIfPresent(values, "remapperFile", inputs.remapperJar());
        putIfPresent(values, "mappingsFile", inputs.mappingsFile());
        putIfPresent(values, "paramsFile", inputs.paramMappingsFile());
        putIfPresent(values, "constantsFile", inputs.constantsFile());
        return values;
    }

    /**
     * Fills the placeholders of each argument.
     *
     * @throws IOException if an argument uses a placeholder with no value, which means the mache build declares a
     *     dependency McDeob failed to resolve
     */
    private static List<String> substitute(final List<String> templates, final Map<String, String> values)
            throws IOException {
        final List<String> arguments = new ArrayList<>(templates.size());
        for (final String template : templates) {
            String argument = template;
            for (final Map.Entry<String, String> value : values.entrySet()) {
                argument = argument.replace('{' + value.getKey() + '}', value.getValue());
            }

            if (argument.indexOf('{') >= 0) {
                throw new IOException("mache.json codebook argument '" + template
                        + "' refers to a file this mache build does not declare");
            }

            // An empty value means the option has nothing to point at, and codebook rejects a blank path.
            if (argument.endsWith("=")) {
                log.debug("Skipping codebook argument '{}' because it resolved to an empty value", template);
                continue;
            }
            arguments.add(argument);
        }
        return arguments;
    }

    private static void putIfPresent(final Map<String, String> values, final String key, @Nullable final Path path) {
        if (path != null) {
            values.put(key, absolute(path));
        }
    }

    private static String joinClasspath(final List<Path> jars) {
        final StringBuilder builder = new StringBuilder();
        for (final Path jar : jars) {
            if (!builder.isEmpty()) {
                builder.append(CODEBOOK_CLASSPATH_SEPARATOR);
            }
            builder.append(absolute(jar));
        }
        return builder.toString();
    }

    private static String absolute(final Path path) {
        return path.toAbsolutePath().toString();
    }

    /**
     * The files codebook's arguments can refer to.
     *
     * @param codebookJar the executable codebook distribution
     * @param remapperJar the remapper jar for codebook 1, or {@code null} for codebook 2, which bundles its own
     * @param mappingsFile Mojang's ProGuard mappings, or {@code null} when codebook resolves them itself
     * @param paramMappingsFile TinyV2 parameter mappings, or {@code null} when the build declares none
     * @param constantsFile the unpick definitions jar, or {@code null} when the build does not unpick
     * @param inputJar the obfuscated server jar
     * @param classpathJars the server's libraries
     * @param outputJar where the remapped jar is written
     * @param tempDirectory codebook's scratch directory, also its working directory
     * @param reportsDirectory where codebook writes reports, when the build asks for them
     */
    record Inputs(
            Path codebookJar,
            @Nullable Path remapperJar,
            @Nullable Path mappingsFile,
            @Nullable Path paramMappingsFile,
            @Nullable Path constantsFile,
            Path inputJar,
            List<Path> classpathJars,
            Path outputJar,
            Path tempDirectory,
            Path reportsDirectory) {

        Inputs {
            classpathJars = List.copyOf(classpathJars);
        }
    }
}
