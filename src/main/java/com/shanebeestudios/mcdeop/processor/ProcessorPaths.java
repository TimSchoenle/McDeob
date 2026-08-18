package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.util.Util;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

record ProcessorPaths(
        Path dataFolderPath,
        Path jarPath,
        Path mappingsPath,
        Path remappedJar,
        Path extractedServerJar,
        Path decompiledJarPath,
        Path decompiledZipPath,
        Path librariesPath,
        Path gradleProjectPath) {

    /**
     * Resolves the output directory for a target and version.
     *
     * <p>Public so the UI can point the user at the output without recomputing the layout. It used to
     * duplicate this format, which meant a change here silently sent the "open output folder" action
     * to a directory that does not exist.
     *
     * @param type the target being processed
     * @param version the Minecraft version being processed
     * @return the directory this run reads from and writes to
     */
    static Path resolveDataFolder(final SourceType type, final Version version) {
        final String versionFolder = String.format("%s-%s", type.name().toLowerCase(Locale.ENGLISH), version.id());
        return Util.getBaseDataFolder().resolve(versionFolder);
    }

    static ProcessorPaths create(final ResourceRequest request) {
        final Path dataFolderPath = resolveDataFolder(request.type(), request.getVersion());
        try {
            Files.createDirectories(dataFolderPath);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to create data directory: " + dataFolderPath, exception);
        }

        return new ProcessorPaths(
                dataFolderPath,
                dataFolderPath.resolve("source.jar"),
                dataFolderPath.resolve("mappings.txt"),
                dataFolderPath.resolve("remapped.jar"),
                dataFolderPath.resolve("server.jar"),
                dataFolderPath.resolve("decompiled"),
                dataFolderPath.resolve("decompiled.zip"),
                dataFolderPath.resolve("libraries"),
                dataFolderPath.resolve("gradle-project"));
    }
}
