package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.util.Util;
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
        Path gradleProjectPath,
        Path machePath,
        Path toolCachePath,
        Path javaRuntimePath) {

    static ProcessorPaths create(final ResourceRequest request) {
        final String versionFolder = String.format(
                "%s-%s",
                request.type().name().toLowerCase(Locale.ENGLISH),
                request.getVersion().id());
        final Path baseFolderPath = Util.getBaseDataFolder();
        final Path dataFolderPath = baseFolderPath.resolve(versionFolder);
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
                dataFolderPath.resolve("gradle-project"),
                dataFolderPath.resolve("mache"),
                // The downloaded toolchain and Java runtime are identical for every version, so they live
                // beside the version folders instead of inside them.
                baseFolderPath.resolve("tool-cache"),
                baseFolderPath.resolve("java-runtime"));
    }
}
