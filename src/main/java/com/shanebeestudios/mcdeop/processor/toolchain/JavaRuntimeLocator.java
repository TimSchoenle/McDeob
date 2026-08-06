package com.shanebeestudios.mcdeop.processor.toolchain;

import com.shanebeestudios.mcdeop.util.NativeImageUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a Java launcher that is new enough to run the PaperMC toolchain.
 *
 * <p>The jar distribution always has one: the JVM it is running on. Native images do not embed a JVM, so the
 * search covers the places a desktop user's JDK realistically lives before {@link JavaRuntimeProvisioner}
 * downloads one.
 */
@Slf4j
public final class JavaRuntimeLocator {
    /** Codebook and Vineflower are both built for Java 21. */
    public static final int MINIMUM_FEATURE_VERSION = 21;

    private static final String JAVA_HOME_PROPERTY = "mcdeob.java.home";
    private static final long PROBE_TIMEOUT_SECONDS = 20;

    private final @Nullable Path explicitJavaHome;

    /**
     * @param explicitJavaHome a user-supplied Java home that takes precedence over everything else, or
     *     {@code null} to fall back to the {@code mcdeob.java.home} system property
     */
    public JavaRuntimeLocator(@Nullable final Path explicitJavaHome) {
        this.explicitJavaHome = explicitJavaHome != null ? explicitJavaHome : systemPropertyJavaHome();
    }

    /**
     * Searches for a usable launcher.
     *
     * @return the first candidate that reports at least {@link #MINIMUM_FEATURE_VERSION}, or empty if none does
     */
    public Optional<JavaRuntime> locate() {
        if (this.explicitJavaHome != null) {
            final Optional<JavaRuntime> configured = probe(launcherIn(this.explicitJavaHome));
            if (configured.isPresent()) {
                return configured;
            }
            log.warn(
                    "Configured Java home {} does not contain a usable Java {}+ launcher, continuing the search.",
                    this.explicitJavaHome,
                    MINIMUM_FEATURE_VERSION);
        }

        for (final Path candidate : this.candidateLaunchers()) {
            final Optional<JavaRuntime> runtime = probe(candidate);
            if (runtime.isPresent()) {
                return runtime;
            }
        }

        return Optional.empty();
    }

    /**
     * Asks a launcher which Java version it is.
     *
     * @param launcher path to a {@code java} executable
     * @return the runtime if the launcher exists and is at least {@link #MINIMUM_FEATURE_VERSION}
     */
    public static Optional<JavaRuntime> probe(final Path launcher) {
        if (!Files.isRegularFile(launcher)) {
            return Optional.empty();
        }

        // The running JVM does not need to be forked to be identified, and in the jar distribution this is
        // the branch that answers.
        if (!NativeImageUtil.isNativeImage() && isCurrentRuntime(launcher)) {
            final int feature = Runtime.version().feature();
            return feature >= MINIMUM_FEATURE_VERSION
                    ? Optional.of(new JavaRuntime(launcher, feature))
                    : Optional.empty();
        }

        return readFeatureVersion(launcher)
                .filter(feature -> feature >= MINIMUM_FEATURE_VERSION)
                .map(feature -> new JavaRuntime(launcher, feature));
    }

    /** Resolves the {@code java} launcher inside a Java home, using the platform's executable name. */
    public static Path launcherIn(final Path javaHome) {
        final String launcherName = isWindows() ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(launcherName);
    }

    private static boolean isCurrentRuntime(final Path launcher) {
        final String currentJavaHome = System.getProperty("java.home");
        if (currentJavaHome == null) {
            return false;
        }

        try {
            return Files.isSameFile(launcher, launcherIn(Paths.get(currentJavaHome)));
        } catch (final IOException exception) {
            return false;
        }
    }

    /**
     * Runs the launcher to read its {@code java.specification.version}.
     *
     * <p>{@code -XshowSettings:properties} is used rather than {@code -version} because its output is a stable
     * property dump, while the {@code -version} banner differs between vendors.
     */
    private static Optional<Integer> readFeatureVersion(final Path launcher) {
        final List<String> command = List.of(launcher.toString(), "-XshowSettings:properties", "-version");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final StringBuilder output = new StringBuilder();
            try (final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Timed out while probing Java launcher {}", launcher);
                return Optional.empty();
            }

            return parseFeatureVersion(output.toString());
        } catch (final IOException exception) {
            log.debug("Could not probe Java launcher {}: {}", launcher, exception.getMessage());
            return Optional.empty();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Reads the feature version out of a {@code -XshowSettings:properties} dump.
     *
     * @param output the launcher's combined output
     * @return the feature version, or empty if the property is missing or unparseable
     */
    static Optional<Integer> parseFeatureVersion(final String output) {
        for (final String rawLine : output.split("\\R")) {
            final String line = rawLine.trim();
            if (!line.startsWith("java.specification.version")) {
                continue;
            }

            final int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }

            // Releases up to 8 report "1.8"; everything since reports the feature number alone.
            String value = line.substring(separator + 1).trim();
            if (value.startsWith("1.")) {
                value = value.substring(2);
            }

            try {
                return Optional.of(Integer.parseInt(value));
            } catch (final NumberFormatException exception) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private List<Path> candidateLaunchers() {
        final Set<Path> candidates = new LinkedHashSet<>();

        if (!NativeImageUtil.isNativeImage()) {
            addJavaHome(candidates, System.getProperty("java.home"));
        }
        addJavaHome(candidates, System.getenv("JAVA_HOME"));
        candidates.addAll(launchersOnPath());
        for (final Path root : wellKnownInstallRoots()) {
            candidates.addAll(launchersUnder(root));
        }

        return new ArrayList<>(candidates);
    }

    private static void addJavaHome(final Set<Path> candidates, @Nullable final String javaHome) {
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(launcherIn(Paths.get(javaHome)));
        }
    }

    private static List<Path> launchersOnPath() {
        final String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }

        final String launcherName = isWindows() ? "java.exe" : "java";
        final List<Path> launchers = new ArrayList<>();
        for (final String entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }

            try {
                final Path launcher = Paths.get(entry.trim()).resolve(launcherName);
                if (Files.isRegularFile(launcher)) {
                    launchers.add(launcher);
                }
            } catch (final RuntimeException exception) {
                log.debug("Ignoring unusable PATH entry {}", entry);
            }
        }
        return launchers;
    }

    /**
     * Directories in which the platform's installers place JDKs.
     *
     * <p>Only one directory level below each root is inspected, which is where every mainstream installer puts
     * the Java home.
     */
    private static List<Path> wellKnownInstallRoots() {
        final List<Path> roots = new ArrayList<>();
        if (isWindows()) {
            addIfPresent(roots, System.getenv("ProgramFiles"), "Java");
            addIfPresent(roots, System.getenv("ProgramFiles"), "Eclipse Adoptium");
            addIfPresent(roots, System.getenv("ProgramFiles"), "Microsoft");
            addIfPresent(roots, System.getenv("ProgramFiles"), "Amazon Corretto");
            addIfPresent(roots, System.getenv("ProgramFiles"), "Zulu");
            addIfPresent(roots, System.getenv("LOCALAPPDATA"), "Programs" + File.separator + "Eclipse Adoptium");
        } else if (isMacOs()) {
            roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            addIfPresent(roots, System.getProperty("user.home"), "Library/Java/JavaVirtualMachines");
        } else {
            roots.add(Paths.get("/usr/lib/jvm"));
            roots.add(Paths.get("/usr/java"));
            roots.add(Paths.get("/opt/java"));
        }
        addIfPresent(roots, System.getProperty("user.home"), ".gradle" + File.separator + "jdks");
        addIfPresent(
                roots,
                System.getProperty("user.home"),
                ".sdkman" + File.separator + "candidates" + File.separator + "java");
        return roots;
    }

    private static void addIfPresent(final List<Path> roots, @Nullable final String base, final String child) {
        if (base != null && !base.isBlank()) {
            roots.add(Paths.get(base).resolve(child));
        }
    }

    private static List<Path> launchersUnder(final Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        final List<Path> children = new ArrayList<>();
        try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root)) {
            for (final Path child : directoryStream) {
                children.add(child);
            }
        } catch (final IOException exception) {
            log.debug("Could not list Java installations in {}: {}", root, exception.getMessage());
            return List.of();
        }
        Collections.sort(children);

        final List<Path> launchers = new ArrayList<>();
        for (final Path child : children) {
            if (!Files.isDirectory(child)) {
                continue;
            }

            final Path direct = launcherIn(child);
            if (Files.isRegularFile(direct)) {
                launchers.add(direct);
            }

            // macOS bundles keep the Java home one level deeper.
            final Path bundled = launcherIn(child.resolve("Contents").resolve("Home"));
            if (Files.isRegularFile(bundled)) {
                launchers.add(bundled);
            }
        }
        return launchers;
    }

    private static @Nullable Path systemPropertyJavaHome() {
        final String configured = System.getProperty(JAVA_HOME_PROPERTY);
        return configured == null || configured.isBlank() ? null : Paths.get(configured.trim());
    }

    private static boolean isWindows() {
        return osName().contains("win");
    }

    private static boolean isMacOs() {
        return osName().contains("mac");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
    }
}
