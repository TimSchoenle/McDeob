package com.shanebeestudios.mcdeop.processor.toolchain;

import com.shanebeestudios.mcdeop.util.ArchiveUtil;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.HttpDownloader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Downloads an Eclipse Temurin JRE into McDeob's data folder.
 *
 * <p>Native images ship no JVM, so without this the PaperMC toolchain would be unusable on any machine that has
 * no JDK installed. The runtime is fetched once and reused by every later run.
 */
@Slf4j
public final class JavaRuntimeProvisioner {
    private static final String ADOPTIUM_BINARY_URL =
            "https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jre/hotspot/normal/eclipse";

    /** Written only after a downloaded runtime has been verified, so a failed attempt is never reused. */
    private static final String READY_MARKER = ".mcdeob-ready";

    /** Java homes sit at most two levels down: {@code jdk-21…/} on most platforms, plus {@code Contents/Home}. */
    private static final int LAUNCHER_SEARCH_DEPTH = 3;

    private final HttpDownloader downloader;
    private final Path runtimeDirectory;

    /**
     * @param downloader used to fetch the runtime archive
     * @param runtimeDirectory directory that holds provisioned runtimes; shared across Minecraft versions
     */
    public JavaRuntimeProvisioner(final HttpDownloader downloader, final Path runtimeDirectory) {
        this.downloader = downloader;
        this.runtimeDirectory = runtimeDirectory;
    }

    /** Returns an already provisioned runtime, without contacting the network. */
    public Optional<JavaRuntime> findProvisioned() {
        final Path installDirectory = this.installDirectory();
        if (!Files.isRegularFile(installDirectory.resolve(READY_MARKER))) {
            return Optional.empty();
        }

        return findLauncher(installDirectory).flatMap(JavaRuntimeLocator::probe);
    }

    /**
     * Downloads and unpacks a Temurin JRE.
     *
     * @return the provisioned runtime
     * @throws IOException if the platform is unsupported, or the download or unpacking fails
     */
    public JavaRuntime provision() throws IOException {
        final String operatingSystem = adoptiumOperatingSystem();
        final String architecture = adoptiumArchitecture();
        final int feature = JavaRuntimeLocator.MINIMUM_FEATURE_VERSION;

        final String url = String.format(ADOPTIUM_BINARY_URL, feature, operatingSystem, architecture);
        final Path archive = this.runtimeDirectory.resolve("temurin-" + feature + "-jre" + archiveExtension());
        final Path installDirectory = this.installDirectory();

        log.info("Downloading a Java {} runtime for {}/{} from Adoptium.", feature, operatingSystem, architecture);
        Files.createDirectories(this.runtimeDirectory);
        this.downloader.download(url, archive);

        log.info("Unpacking the Java runtime into {}", installDirectory);
        FileUtil.remove(installDirectory);
        ArchiveUtil.extract(archive, installDirectory);
        Files.deleteIfExists(archive);

        final Path launcher = findLauncher(installDirectory)
                .orElseThrow(
                        () -> new IOException("Downloaded Java runtime contains no launcher: " + installDirectory));
        ArchiveUtil.makeExecutable(launcher);

        final JavaRuntime runtime = JavaRuntimeLocator.probe(launcher)
                .orElseThrow(() -> new IOException("Downloaded Java runtime is not usable: " + launcher));

        Files.createFile(installDirectory.resolve(READY_MARKER));
        log.info("Provisioned Java {} at {}", runtime.featureVersion(), runtime.executable());
        return runtime;
    }

    private Path installDirectory() {
        return this.runtimeDirectory.resolve("temurin-" + JavaRuntimeLocator.MINIMUM_FEATURE_VERSION + "-jre");
    }

    /**
     * Finds the {@code java} launcher inside an extracted runtime.
     *
     * <p>Archive layouts differ per platform, so the search is breadth-first and returns the shallowest match,
     * which is the real Java home rather than a nested copy.
     */
    private static Optional<Path> findLauncher(final Path installDirectory) {
        final Deque<Path> queue = new ArrayDeque<>();
        queue.add(installDirectory);
        int depth = 0;

        while (!queue.isEmpty() && depth <= LAUNCHER_SEARCH_DEPTH) {
            final List<Path> level = new ArrayList<>(queue);
            queue.clear();

            for (final Path directory : level) {
                final Path launcher = JavaRuntimeLocator.launcherIn(directory);
                if (Files.isRegularFile(launcher)) {
                    return Optional.of(launcher);
                }
                queue.addAll(childDirectories(directory));
            }
            depth++;
        }

        return Optional.empty();
    }

    private static List<Path> childDirectories(final Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        final List<Path> children = new ArrayList<>();
        try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
            for (final Path child : directoryStream) {
                if (Files.isDirectory(child)) {
                    children.add(child);
                }
            }
        } catch (final IOException exception) {
            log.debug("Could not list {}: {}", directory, exception.getMessage());
            return List.of();
        }

        Collections.sort(children);
        return children;
    }

    private static String archiveExtension() {
        return osName().contains("win") ? ".zip" : ".tar.gz";
    }

    private static String adoptiumOperatingSystem() throws IOException {
        final String osName = osName();
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac")) {
            return "mac";
        }
        if (osName.contains("linux")) {
            return "linux";
        }
        throw new IOException("No Adoptium Java runtime is available for this operating system: " + osName);
    }

    private static String adoptiumArchitecture() throws IOException {
        final String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        return switch (architecture) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default ->
                throw new IOException("No Adoptium Java runtime is available for this architecture: " + architecture);
        };
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
    }
}
