package de.timmi6790.launchermeta.data.version;

import java.util.List;

public record VersionManifest(Latest latest, List<Version> versions) {

    public record Latest(String release, String snapshot) {}
}
