package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.util.HttpDownloader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Downloads the tool artifacts mache pins, caching them under McDeob's data folder.
 *
 * <p>Deliberately not a general dependency resolver: mache declares every artifact it needs with an exact version
 * and expects them resolved without transitive dependencies, exactly as PaperMC's build does.
 */
@Slf4j
final class MavenArtifactResolver {
    /**
     * Repositories that serve the toolchain even when a mache build does not list them.
     *
     * <p>Mirrors sculptor's own defaults so an older or hand-edited {@code mache.json} still resolves, with Maven
     * Central last because mache's decompiler is published there and nowhere else.
     */
    private static final List<MacheRepository> DEFAULT_REPOSITORIES = List.of(
            new MacheRepository("PaperMC", "https://repo.papermc.io/repository/maven-public/", List.of("io.papermc")),
            new MacheRepository(
                    "NeoForged",
                    "https://maven.neoforged.net/releases/",
                    List.of("net.neoforged", "net.minecraftforge")),
            new MacheRepository("FabricMC", "https://maven.fabricmc.net/", List.of("net.fabricmc")),
            new MacheRepository("ParchmentMC", "https://maven.parchmentmc.org/", List.of("org.parchmentmc")),
            new MacheRepository("MavenCentral", "https://repo1.maven.org/maven2/", List.of()));

    private final HttpDownloader downloader;
    private final List<MacheRepository> repositories;
    private final Path cacheDirectory;

    /**
     * @param downloader used for both metadata and artifact requests
     * @param declaredRepositories repositories declared by the mache build, tried before the defaults
     * @param cacheDirectory root of a Maven-layout cache, shared across Minecraft versions
     */
    MavenArtifactResolver(
            final HttpDownloader downloader,
            final List<MacheRepository> declaredRepositories,
            final Path cacheDirectory) {
        this.downloader = downloader;
        this.cacheDirectory = cacheDirectory;

        final Set<MacheRepository> merged = new LinkedHashSet<>(declaredRepositories);
        merged.addAll(DEFAULT_REPOSITORIES);
        this.repositories = List.copyOf(merged);
    }

    /**
     * Resolves an artifact to a local file, downloading it if it is not cached.
     *
     * @param artifact the artifact to resolve
     * @return the local path of the artifact
     * @throws IOException if no repository serves the artifact
     */
    Path resolve(final MavenArtifact artifact) throws IOException {
        if (artifact.extension() != null && !artifact.isSnapshot()) {
            final Path cached = this.cachePath(artifact, artifact.version());
            if (isPresent(cached)) {
                log.debug("Reusing cached {}", artifact.coordinates());
                return cached;
            }
        }

        final List<String> attempted = new ArrayList<>();
        for (final MacheRepository repository : this.repositories) {
            if (!repository.serves(artifact.group())) {
                continue;
            }

            final Optional<Path> resolved = this.resolveFrom(repository, artifact);
            if (resolved.isPresent()) {
                return resolved.get();
            }
            attempted.add(repository.name());
        }

        throw new IOException(
                "Could not resolve " + artifact.coordinates() + " from any repository (tried: " + attempted + ")");
    }

    /**
     * Attempts to resolve an artifact from a single repository.
     *
     * @return the local path, or empty if this repository does not publish the artifact
     */
    private Optional<Path> resolveFrom(final MacheRepository repository, final MavenArtifact artifact)
            throws IOException {
        final @Nullable MavenMetadata snapshotMetadata =
                artifact.isSnapshot() ? this.readSnapshotMetadata(repository, artifact) : null;
        if (artifact.isSnapshot() && snapshotMetadata == null) {
            return Optional.empty();
        }

        final MavenArtifact resolved = artifact.extension() != null
                ? artifact
                : artifact.withExtension(this.readPackaging(repository, artifact, snapshotMetadata));

        final Optional<String> fileVersion = fileVersion(resolved, snapshotMetadata);
        if (fileVersion.isEmpty()) {
            return Optional.empty();
        }

        final String url = repository.url() + resolved.versionPath() + '/' + resolved.fileName(fileVersion.get());
        final Path target = this.cachePath(resolved, fileVersion.get());
        try {
            if (this.downloader.downloadIfAbsent(url, target)) {
                log.info("Downloaded {} from {}", resolved.coordinates(), repository.name());
            }
            return Optional.of(target);
        } catch (final FileNotFoundException exception) {
            return Optional.empty();
        }
    }

    /**
     * Determines the version to use in the artifact's file name.
     *
     * @return the artifact version for releases, the timestamped build version for snapshots, or empty when the
     *     repository publishes no matching snapshot
     */
    private static Optional<String> fileVersion(
            final MavenArtifact artifact, @Nullable final MavenMetadata snapshotMetadata) {
        if (snapshotMetadata == null) {
            return Optional.of(artifact.version());
        }
        return snapshotMetadata.snapshotVersion(
                artifact.extension() == null ? MavenArtifact.DEFAULT_EXTENSION : artifact.extension(),
                artifact.classifier());
    }

    private @Nullable MavenMetadata readSnapshotMetadata(final MacheRepository repository, final MavenArtifact artifact)
            throws IOException {
        final String url = repository.url() + artifact.versionPath() + "/maven-metadata.xml";
        return this.downloader.readDocument(url).map(MavenMetadata::of).orElse(null);
    }

    /**
     * Reads the packaging an artifact's POM declares.
     *
     * <p>mache leaves the extension out of its metadata and lets Gradle take it from the POM, which matters for
     * artifacts that are not jars: parameter mappings, for instance, are published as a zip.
     *
     * @return the declared packaging, or {@link MavenArtifact#DEFAULT_EXTENSION} when the POM is missing or
     *     declares none, which is also Maven's own default
     */
    private String readPackaging(
            final MacheRepository repository,
            final MavenArtifact artifact,
            @Nullable final MavenMetadata snapshotMetadata)
            throws IOException {
        final Optional<String> pomVersion = snapshotMetadata == null
                ? Optional.of(artifact.version())
                : snapshotMetadata.snapshotVersion(MavenArtifact.POM_EXTENSION, null);
        if (pomVersion.isEmpty()) {
            return MavenArtifact.DEFAULT_EXTENSION;
        }

        final String fileName = artifact.pomFileName(pomVersion.get());
        final Path target = this.cacheDirectory.resolve(artifact.versionPath()).resolve(fileName);
        try {
            this.downloader.downloadIfAbsent(repository.url() + artifact.versionPath() + '/' + fileName, target);
        } catch (final FileNotFoundException exception) {
            return MavenArtifact.DEFAULT_EXTENSION;
        }

        final String packaging = XmlElements.first(Files.readString(target, StandardCharsets.UTF_8), "packaging");
        return packaging == null || packaging.isBlank() ? MavenArtifact.DEFAULT_EXTENSION : packaging;
    }

    private Path cachePath(final MavenArtifact artifact, final String fileVersion) {
        return this.cacheDirectory.resolve(artifact.versionPath()).resolve(artifact.fileName(fileVersion));
    }

    private static boolean isPresent(final Path path) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) > 0;
    }

    /**
     * Reads the published version list of an artifact.
     *
     * @param group group id
     * @param name artifact id
     * @return metadata from the first serving repository that hosts the artifact, or empty if none does
     */
    Optional<MavenMetadata> readVersionMetadata(final String group, final String name) throws IOException {
        for (final MacheRepository repository : this.repositories) {
            if (!repository.serves(group)) {
                continue;
            }

            final String url = repository.url() + group.replace('.', '/') + '/' + name + "/maven-metadata.xml";
            final Optional<byte[]> document = this.downloader.readDocument(url);
            if (document.isPresent()) {
                return Optional.of(MavenMetadata.of(document.get()));
            }
        }

        return Optional.empty();
    }
}
