package com.shanebeestudios.mcdeop.processor;

import de.timmi6790.launchermeta.data.release.DownloadInfo;
import de.timmi6790.launchermeta.data.release.Downloads;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.net.URL;
import java.util.Optional;

public record ResourceRequest(ReleaseManifest manifest, SourceType type) {
    public Version getVersion() {
        return this.manifest.getVersion();
    }

    public Optional<URL> getJar() {
        final Downloads downloads = this.manifest.getDownloads();
        return Optional.ofNullable(
                        switch (this.type) {
                            case SERVER -> downloads.server();
                            case CLIENT -> downloads.client();
                        })
                .map(DownloadInfo::url);
    }

    public Optional<URL> getMappings() {
        final Downloads downloads = this.manifest.getDownloads();
        return switch (this.type) {
            case SERVER -> downloads.getServerMappings().map(DownloadInfo::url);
            case CLIENT -> downloads.getClientMappings().map(DownloadInfo::url);
        };
    }
}
