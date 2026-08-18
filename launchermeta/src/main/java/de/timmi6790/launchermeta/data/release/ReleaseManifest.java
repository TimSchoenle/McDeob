package de.timmi6790.launchermeta.data.release;

import de.timmi6790.launchermeta.data.version.Version;
import java.util.List;

public record ReleaseManifest(
        Downloads downloads, String mainClass, List<Library> libraries, JavaVersion javaVersion, Version version) {}
