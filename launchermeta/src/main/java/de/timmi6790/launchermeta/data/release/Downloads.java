package de.timmi6790.launchermeta.data.release;

import java.util.Optional;

public record Downloads(
        DownloadInfo client, DownloadInfo clientMappings, DownloadInfo server, DownloadInfo serverMappings) {

    public Optional<DownloadInfo> getClientMappings() {
        return Optional.ofNullable(this.clientMappings);
    }

    public Optional<DownloadInfo> getServerMappings() {
        return Optional.ofNullable(this.serverMappings);
    }
}
