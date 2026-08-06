package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.util.ArchiveUtil;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.HttpDownloader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/** Resolves, downloads and unpacks the mache artifact for a Minecraft version. */
@Slf4j
public final class MacheBundleService {
    private static final String MACHE_GROUP = "io.papermc";
    private static final String MACHE_NAME = "mache";
    private static final String MACHE_EXTENSION = "zip";
    private static final String BUILD_SEPARATOR = "+build.";
    private static final String META_FILE = "mache.json";
    private static final String PATCHES_DIRECTORY = "patches";

    private final MavenArtifactResolver resolver;

    /**
     * @param downloader used for metadata and the mache archive
     * @param cacheDirectory Maven-layout cache shared across Minecraft versions
     */
    public MacheBundleService(final HttpDownloader downloader, final Path cacheDirectory) {
        this.resolver = new MavenArtifactResolver(downloader, List.of(), cacheDirectory);
    }

    /**
     * Downloads and unpacks the newest mache build for a Minecraft version.
     *
     * @param minecraftVersion the Minecraft version id, as Mojang names it
     * @param extractionDirectory directory the archive is unpacked into; replaced if it already exists
     * @return the unpacked bundle
     * @throws MacheUnavailableException if no mache build exists for the version
     * @throws IOException if the archive cannot be downloaded or read
     */
    public MacheBundle resolve(final String minecraftVersion, final Path extractionDirectory) throws IOException {
        final MavenArtifact artifact = this.resolveArtifact(minecraftVersion);
        log.info("Using mache {}", artifact.version());

        final Path archive = this.resolver.resolve(artifact);

        FileUtil.remove(extractionDirectory);
        ArchiveUtil.unzip(archive, extractionDirectory);

        final Path metaFile = extractionDirectory.resolve(META_FILE);
        if (!Files.isRegularFile(metaFile)) {
            throw new IOException("mache archive " + artifact.version() + " contains no " + META_FILE);
        }

        final MacheMeta meta = MacheMetaParser.parse(Files.readAllBytes(metaFile));
        if (!meta.minecraftVersion().equals(minecraftVersion)) {
            throw new IOException("mache " + artifact.version() + " targets Minecraft " + meta.minecraftVersion()
                    + ", not " + minecraftVersion);
        }

        return new MacheBundle(artifact, meta, resolvePatchesDirectory(extractionDirectory));
    }

    /**
     * Picks the highest published build of mache for a Minecraft version.
     *
     * <p>mache versions are the Minecraft version followed by a build number, and later builds carry fixes to the
     * patches or bumps of the pinned tools, so the newest build is always the one to use.
     */
    private MavenArtifact resolveArtifact(final String minecraftVersion) throws IOException {
        final Optional<MavenMetadata> metadata = this.resolver.readVersionMetadata(MACHE_GROUP, MACHE_NAME);
        if (metadata.isEmpty()) {
            throw new IOException("Could not read the published mache version list");
        }

        final String prefix = minecraftVersion + BUILD_SEPARATOR;
        String bestVersion = null;
        int bestBuild = -1;
        for (final String version : metadata.get().versions()) {
            if (!version.startsWith(prefix)) {
                continue;
            }

            final int build = parseBuildNumber(version.substring(prefix.length()));
            if (build > bestBuild) {
                bestBuild = build;
                bestVersion = version;
            }
        }

        if (bestVersion == null) {
            throw new MacheUnavailableException(
                    "PaperMC has published no mache build for Minecraft " + minecraftVersion);
        }

        return new MavenArtifact(MACHE_GROUP, MACHE_NAME, bestVersion, null, MACHE_EXTENSION);
    }

    /**
     * @param suffix the part of a mache version after {@value #BUILD_SEPARATOR}
     * @return the build number, or {@code -1} if it is not a plain number
     */
    private static int parseBuildNumber(final String suffix) {
        try {
            return Integer.parseInt(suffix);
        } catch (final NumberFormatException exception) {
            return -1;
        }
    }

    private static @Nullable Path resolvePatchesDirectory(final Path extractionDirectory) throws IOException {
        final Path patches = extractionDirectory.resolve(PATCHES_DIRECTORY);
        if (!Files.isDirectory(patches)) {
            return null;
        }

        try (final DirectoryStream<Path> entries = Files.newDirectoryStream(patches)) {
            return entries.iterator().hasNext() ? patches : null;
        }
    }
}
