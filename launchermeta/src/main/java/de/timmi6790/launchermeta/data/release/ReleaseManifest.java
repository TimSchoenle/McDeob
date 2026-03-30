package de.timmi6790.launchermeta.data.release;

import de.timmi6790.launchermeta.data.version.Version;

public class ReleaseManifest {
    private final Downloads downloads;
    private final String mainClass;
    private Version version;

    public ReleaseManifest(final Downloads downloads, final String mainClass) {
        this.downloads = downloads;
        this.mainClass = mainClass;
    }

    public Downloads getDownloads() {
        return this.downloads;
    }

    public String getMainClass() {
        return this.mainClass;
    }

    public Version getVersion() {
        return this.version;
    }

    public void setVersion(final Version version) {
        this.version = version;
    }
}
