package com.shanebeestudios.mcdeop.processor;

import de.timmi6790.launchermeta.data.release.DownloadInfo;
import de.timmi6790.launchermeta.data.release.Downloads;
import de.timmi6790.launchermeta.data.release.JavaVersion;
import de.timmi6790.launchermeta.data.release.Library;
import de.timmi6790.launchermeta.data.release.LibraryArtifact;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ResourceRequest(ReleaseManifest manifest, SourceType type) {
    public Version getVersion() {
        return this.manifest.version();
    }

    /**
     * The jar download for the selected target.
     *
     * <p>Returns the full {@link DownloadInfo} rather than only its URL so the advertised size and
     * SHA-1 digest stay available for verification after the download.
     *
     * @return the jar download, or empty when the manifest does not list one
     */
    public Optional<DownloadInfo> getJar() {
        final Downloads downloads = this.manifest.downloads();
        return Optional.ofNullable(
                switch (this.type) {
                    case SERVER -> downloads.server();
                    case CLIENT -> downloads.client();
                });
    }

    public Optional<DownloadInfo> getMappings() {
        final Downloads downloads = this.manifest.downloads();
        return switch (this.type) {
            case SERVER -> downloads.getServerMappings();
            case CLIENT -> downloads.getClientMappings();
        };
    }

    public List<LibraryArtifact> getLibraries() {
        final List<LibraryArtifact> artifacts = new ArrayList<>();
        for (final Library library : this.manifest.libraries()) {
            final LibraryArtifact artifact = library.artifact();
            if (artifact != null) {
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    public Optional<String> getMainClass() {
        return Optional.ofNullable(this.manifest.mainClass()).filter(mainClass -> !mainClass.isBlank());
    }

    public Optional<Integer> getJavaVersion() {
        return Optional.ofNullable(this.manifest.javaVersion())
                .map(JavaVersion::majorVersion)
                .filter(version -> version > 0);
    }
}
