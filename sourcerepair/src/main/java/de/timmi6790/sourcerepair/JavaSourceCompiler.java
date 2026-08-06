package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Type-checks a source tree and reports what the compiler rejects.
 *
 * <p>Which compiler does the work depends on how McDeob was started. Running on a JDK, the one built
 * into the runtime is used directly. A native image has no compiler of its own, so a JDK installed
 * on the machine is borrowed instead — otherwise the feature would be unavailable in exactly the
 * distribution most users run.
 */
interface JavaSourceCompiler {

    Logger LOG = LoggerFactory.getLogger(JavaSourceCompiler.class);

    /** Effectively unlimited; the loop needs every error of a round, not the first hundred. */
    String MAX_ERRORS = "100000";

    /**
     * Compiles a source tree and collects the errors.
     *
     * @param sources the source files to compile
     * @param classpath jars the sources compile against
     * @return every error reported, in the order the compiler produced them
     * @throws IOException if the compiler cannot be run
     */
    List<CompileDiagnostic> compile(List<Path> sources, List<Path> classpath) throws IOException;

    /**
     * Finds a compiler able to build the given source level.
     *
     * @param release the Java release the sources target
     * @return a compiler, or {@code null} if the machine offers none new enough
     */
    static JavaSourceCompiler create(final int release) {
        final InProcessJavaCompiler inProcess = InProcessJavaCompiler.create(release);
        if (inProcess != null) {
            return inProcess;
        }

        final ExternalJavaCompiler external = ExternalJavaCompiler.create(release);
        if (external != null) {
            return external;
        }

        LOG.warn(
                "No Java {} compiler found, so the sources cannot be type-checked and are left unrepaired."
                        + " Install a JDK {} or newer, or point JAVA_HOME at one, to enable source repair.",
                release,
                release);
        return null;
    }

    /**
     * The compiler options a repair round runs with, identical whichever compiler is used.
     *
     * @param release the Java release the sources target
     * @param classpath jars the sources compile against
     * @return the options, ready to pass to the compiler
     */
    static List<String> optionsFor(final int release, final List<Path> classpath) {
        final List<String> options = new ArrayList<>(List.of(
                "--release",
                Integer.toString(release),
                "-encoding",
                StandardCharsets.UTF_8.name(),
                "-proc:none",
                "-nowarn",
                "-Xmaxerrs",
                MAX_ERRORS));

        if (!classpath.isEmpty()) {
            final StringBuilder joined = new StringBuilder();
            for (final Path entry : classpath) {
                if (!joined.isEmpty()) {
                    joined.append(java.io.File.pathSeparatorChar);
                }
                joined.append(entry.toAbsolutePath());
            }
            options.add("-classpath");
            options.add(joined.toString());
        }
        return options;
    }
}
