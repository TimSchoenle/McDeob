package de.timmi6790.launchermeta.data.release;

import de.timmi6790.launchermeta.data.version.Version;
import java.util.List;

public class ReleaseManifest {
    private final Downloads downloads;
    private final String mainClass;
    private final List<Library> libraries;
    private final JavaVersion javaVersion;
    private final Version version;

    public ReleaseManifest(
            final Downloads downloads,
            final String mainClass,
            final List<Library> libraries,
            final JavaVersion javaVersion,
            final Version version) {
        this.downloads = downloads;
        this.mainClass = mainClass;
        this.libraries = libraries;
        this.javaVersion = javaVersion;
        this.version = version;
    }

    public Downloads getDownloads() {
        return this.downloads;
    }

    public String getMainClass() {
        return this.mainClass;
    }

    public List<Library> getLibraries() {
        return this.libraries;
    }

    public JavaVersion getJavaVersion() {
        return this.javaVersion;
    }

    public Version getVersion() {
        return this.version;
    }
}
