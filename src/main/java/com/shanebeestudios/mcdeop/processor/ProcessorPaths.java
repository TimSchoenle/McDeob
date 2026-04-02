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
        Path decompiledJarPath,
        Path decompiledZipPath,
        Path librariesPath,
        Path gradleProjectPath) {

    static ProcessorPaths create(final ResourceRequest request) {
        final String versionFolder = String.format(
                "%s-%s",
                request.type().name().toLowerCase(Locale.ENGLISH),
                request.getVersion().id());
        final Path dataFolderPath = Util.getBaseDataFolder().resolve(versionFolder);
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
                dataFolderPath.resolve("decompiled"),
                dataFolderPath.resolve("decompiled.zip"),
                dataFolderPath.resolve("libraries"),
                dataFolderPath.resolve("gradle-project"));
    }
}
