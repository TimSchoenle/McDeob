package com.shanebeestudios.mcdeop.processor.toolchain;

import com.shanebeestudios.mcdeop.util.HttpDownloader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the Java runtime that the PaperMC toolchain is executed with.
 *
 * <p>An installed JDK is always preferred; a runtime is only downloaded when the machine has none, which is the
 * normal situation for a native image install.
 */
@Slf4j
public final class JavaRuntimeProvider {
    private final JavaRuntimeLocator locator;
    private final JavaRuntimeProvisioner provisioner;

    /**
     * @param downloader used when a runtime has to be fetched
     * @param runtimeDirectory directory for provisioned runtimes, shared across Minecraft versions
     * @param explicitJavaHome a Java home chosen by the user, or {@code null} to search automatically
     */
    public JavaRuntimeProvider(
            final HttpDownloader downloader, final Path runtimeDirectory, @Nullable final Path explicitJavaHome) {
        this.locator = new JavaRuntimeLocator(explicitJavaHome);
        this.provisioner = new JavaRuntimeProvisioner(downloader, runtimeDirectory);
    }

    /**
     * Resolves a Java runtime, downloading one if necessary.
     *
     * @param statusReporter receives a message before the download, which is slow enough to need feedback
     * @return a runtime of at least {@link JavaRuntimeLocator#MINIMUM_FEATURE_VERSION}
     * @throws IOException if no runtime could be found and none could be downloaded
     */
    public JavaRuntime resolve(final Consumer<String> statusReporter) throws IOException {
        final Optional<JavaRuntime> located = this.locator.locate();
        if (located.isPresent()) {
            log.info(
                    "Using Java {} at {} to run the PaperMC toolchain.",
                    located.get().featureVersion(),
                    located.get().executable());
            return located.get();
        }

        final Optional<JavaRuntime> provisioned = this.provisioner.findProvisioned();
        if (provisioned.isPresent()) {
            log.info(
                    "Using the previously downloaded Java {} at {}.",
                    provisioned.get().featureVersion(),
                    provisioned.get().executable());
            return provisioned.get();
        }

        log.info("No Java {}+ installation was found, downloading one.", JavaRuntimeLocator.MINIMUM_FEATURE_VERSION);
        statusReporter.accept("Downloading a Java runtime...");
        return this.provisioner.provision();
    }
}
