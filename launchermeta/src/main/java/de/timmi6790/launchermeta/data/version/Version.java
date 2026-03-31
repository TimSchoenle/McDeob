package de.timmi6790.launchermeta.data.version;

import java.net.URL;
import java.time.OffsetDateTime;

public record Version(String id, VersionType type, URL url, OffsetDateTime releaseTime) {}
