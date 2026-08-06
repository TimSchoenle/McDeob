package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Type-checks a source tree using a JDK installed on the machine.
 *
 * <p>A native image contains no compiler, and adding one would mean building all of {@code
 * jdk.compiler} into the binary. Borrowing an installed JDK avoids that and costs nothing at build
 * time, at the price of requiring one to be present — which is a requirement of the generated
 * project anyway.
 *
 * <p>Rather than parsing console output, a small program is handed to that JDK's source launcher and
 * run there. It drives the same compiler API and reports the same diagnostics, so both compilers
 * produce identical results — including the exact source spans, which console output does not carry
 * and which the repairs depend on.
 */
@Slf4j
final class ExternalJavaCompiler implements JavaSourceCompiler {

    private static final Pattern VERSION = Pattern.compile("(\\d+)");

    private static final String PROBE_NAME = "SourceRepairProbe";

    /** Written to disk and run by the external JDK; see the class javadoc for why. */
    private static final String PROBE_SOURCE = """
            import java.io.File;
            import java.io.OutputStream;
            import java.io.Writer;
            import java.net.URI;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.Locale;
            import javax.tools.Diagnostic;
            import javax.tools.DiagnosticCollector;
            import javax.tools.FileObject;
            import javax.tools.ForwardingJavaFileManager;
            import javax.tools.JavaCompiler;
            import javax.tools.JavaFileManager;
            import javax.tools.JavaFileObject;
            import javax.tools.SimpleJavaFileObject;
            import javax.tools.StandardJavaFileManager;
            import javax.tools.ToolProvider;

            public final class SourceRepairProbe {

                public static void main(final String[] args) throws Exception {
                    final List<String> options = new ArrayList<>();
                    final List<File> sources = new ArrayList<>();
                    for (final String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
                        if (line.startsWith("O:")) {
                            options.add(line.substring(2));
                        } else if (line.startsWith("S:")) {
                            sources.add(new File(line.substring(2)));
                        }
                    }

                    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                    final DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
                    try (StandardJavaFileManager files =
                            compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8)) {
                        final JavaFileManager discarding = new ForwardingJavaFileManager<StandardJavaFileManager>(files) {
                            @Override
                            public JavaFileObject getJavaFileForOutput(
                                    final Location location,
                                    final String className,
                                    final JavaFileObject.Kind kind,
                                    final FileObject sibling) {
                                final URI uri = URI.create("discard:///" + className.replace('.', '/') + kind.extension);
                                return new SimpleJavaFileObject(uri, kind) {
                                    @Override
                                    public OutputStream openOutputStream() {
                                        return OutputStream.nullOutputStream();
                                    }
                                };
                            }
                        };
                        compiler.getTask(
                                        Writer.nullWriter(),
                                        discarding,
                                        collector,
                                        options,
                                        null,
                                        files.getJavaFileObjectsFromFiles(sources))
                                .call();
                    }

                    final StringBuilder report = new StringBuilder();
                    for (final Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                        if (diagnostic.getKind() != Diagnostic.Kind.ERROR || diagnostic.getSource() == null) {
                            continue;
                        }
                        report.append(diagnostic.getLineNumber())
                                .append('\\t')
                                .append(diagnostic.getColumnNumber())
                                .append('\\t')
                                .append(diagnostic.getStartPosition())
                                .append('\\t')
                                .append(diagnostic.getEndPosition())
                                .append('\\t')
                                .append(Path.of(diagnostic.getSource().toUri()))
                                .append('\\t')
                                .append(diagnostic
                                        .getMessage(Locale.ROOT)
                                        .replace("\\\\", "\\\\\\\\")
                                        .replace("\\r", "")
                                        .replace("\\n", "\\\\n"))
                                .append('\\n');
                    }
                    Files.writeString(Path.of(args[1]), report.toString(), StandardCharsets.UTF_8);
                }
            }
            """;

    private final Path javaExecutable;
    private final Path workingDirectory;
    private final int release;

    private ExternalJavaCompiler(final Path javaExecutable, final Path workingDirectory, final int release) {
        this.javaExecutable = javaExecutable;
        this.workingDirectory = workingDirectory;
        this.release = release;
    }

    /**
     * Finds an installed JDK new enough to build the given source level.
     *
     * @param release the Java release the sources target
     * @return the compiler, or {@code null} if no suitable JDK was found
     */
    static ExternalJavaCompiler create(final int release) {
        for (final Path home : candidateJdkHomes()) {
            final Path java = executable(home, "java");
            final Path javac = executable(home, "javac");
            if (!Files.isRegularFile(java) || !Files.isRegularFile(javac)) {
                continue;
            }

            final int feature = featureVersionOf(javac);
            if (feature < release) {
                log.debug("Skipping JDK {}: Java {} cannot build Java {} sources", home, feature, release);
                continue;
            }

            try {
                final Path workingDirectory = Files.createTempDirectory("mcdeob-source-repair");
                Files.writeString(workingDirectory.resolve(PROBE_NAME + ".java"), PROBE_SOURCE, StandardCharsets.UTF_8);
                log.info("Using the JDK at {} to type-check the sources", home);
                return new ExternalJavaCompiler(java, workingDirectory, release);
            } catch (final IOException exception) {
                log.warn("Could not prepare the external compiler", exception);
                return null;
            }
        }
        return null;
    }

    @Override
    public List<CompileDiagnostic> compile(final List<Path> sources, final List<Path> classpath) throws IOException {
        final Path request = this.workingDirectory.resolve("request.txt");
        final Path response = this.workingDirectory.resolve("response.txt");

        final List<String> lines = new ArrayList<>(sources.size() + 16);
        for (final String option : JavaSourceCompiler.optionsFor(this.release, classpath)) {
            lines.add("O:" + option);
        }
        for (final Path source : sources) {
            lines.add("S:" + source.toAbsolutePath());
        }
        Files.write(request, lines, StandardCharsets.UTF_8);
        Files.deleteIfExists(response);

        final Process process = new ProcessBuilder(
                        this.javaExecutable.toString(),
                        this.workingDirectory.resolve(PROBE_NAME + ".java").toString(),
                        request.toString(),
                        response.toString())
                .directory(this.workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(this.workingDirectory.resolve("probe.log").toFile())
                .start();

        try {
            if (process.waitFor() != 0 || !Files.exists(response)) {
                throw new IOException("External compiler failed; see "
                        + this.workingDirectory.resolve("probe.log").toAbsolutePath());
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while type-checking", exception);
        }

        return parse(Files.readAllLines(response, StandardCharsets.UTF_8));
    }

    private static List<CompileDiagnostic> parse(final List<String> lines) {
        final List<CompileDiagnostic> diagnostics = new ArrayList<>(lines.size());
        for (final String line : lines) {
            final String[] fields = line.split("\t", 6);
            if (fields.length < 6) {
                continue;
            }

            diagnostics.add(new CompileDiagnostic(
                    Path.of(fields[4]),
                    Integer.parseInt(fields[0]),
                    Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3]),
                    fields[5].replace("\\n", "\n").replace("\\\\", "\\")));
        }
        return diagnostics;
    }

    /**
     * Places a JDK might be installed, most specific first.
     *
     * <p>The Gradle toolchain directory is included last because a generated project downloads its
     * toolchain there, so a machine that has built one of these projects already has a matching JDK
     * even if nothing else on it does.
     *
     * @return directories that may be JDK homes
     */
    private static List<Path> candidateJdkHomes() {
        final List<Path> homes = new ArrayList<>();
        addIfPresent(homes, System.getenv("JAVA_HOME"));
        addIfPresent(homes, System.getProperty("java.home"));

        final String path = System.getenv("PATH");
        if (path != null) {
            for (final String entry : path.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!entry.isBlank()) {
                    // PATH lists bin directories; the home is their parent.
                    addIfPresent(homes, Path.of(entry).getParent());
                }
            }
        }

        final Path gradleToolchains = Path.of(System.getProperty("user.home", "."), ".gradle", "jdks");
        if (Files.isDirectory(gradleToolchains)) {
            try (var entries = Files.list(gradleToolchains)) {
                entries.filter(Files::isDirectory).forEach(homes::add);
            } catch (final IOException | UncheckedIOException exception) {
                log.debug("Could not list Gradle toolchains", exception);
            }
        }
        return homes;
    }

    private static void addIfPresent(final List<Path> homes, final String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            homes.add(Path.of(candidate));
        }
    }

    private static void addIfPresent(final List<Path> homes, final Path candidate) {
        if (candidate != null) {
            homes.add(candidate);
        }
    }

    private static Path executable(final Path home, final String name) {
        final boolean windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win");
        return home.resolve("bin").resolve(windows ? name + ".exe" : name);
    }

    /**
     * Reads a compiler's feature version.
     *
     * @param javac the compiler executable
     * @return the feature version, or {@code -1} if it could not be determined
     */
    private static int featureVersionOf(final Path javac) {
        try {
            final Process process = new ProcessBuilder(javac.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();

            final Matcher version = VERSION.matcher(output);
            return version.find() ? Integer.parseInt(version.group(1)) : -1;
        } catch (final IOException exception) {
            return -1;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
