package com.shanebeestudios.mcdeop;

import de.timmi6790.launchermeta.LauncherMeta;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionManager {
    private static final OffsetDateTime MINIMUM_RELEASE_TIME = OffsetDateTime.parse("2019-08-28T15:00:00Z");
    // 26.X+ no longer comes with obfuscation
    private static final OffsetDateTime MINIMUM_RELEASE_TIME_WITH_NO_OBFUSCATION =
            OffsetDateTime.parse("2025-12-16T00:00:00Z");
    private static final Set<String> SPECIAL_VERSIONS = Set.of("1.14.4");

    private final LauncherMeta launcherMeta;

    @Getter(lazy = true)
    private final List<Version> versions = this.fetchVersions();

    public VersionManager(final LauncherMeta launcherMeta) {
        this.launcherMeta = launcherMeta;
    }

    public boolean hasMappings(final Version version) {
        if (!this.isSupportedVersion(version)) {
            return false;
        }

        return !version.releaseTime().isAfter(MINIMUM_RELEASE_TIME_WITH_NO_OBFUSCATION);
    }

    public boolean isSupportedVersion(final Version version) {
        return version.releaseTime().isAfter(MINIMUM_RELEASE_TIME) || SPECIAL_VERSIONS.contains(version.id());
    }

    private List<Version> fetchVersions() {
        try {
            return this.launcherMeta.getVersionManifest().versions().stream()
                    .filter(this::isSupportedVersion)
                    .sorted(Comparator.comparing(Version::releaseTime).reversed())
                    .toList();
        } catch (final IOException e) {
            log.error("Failed to fetch version manifest", e);
            return List.of();
        }
    }

    public Optional<Version> getVersion(final String id) {
        return this.getVersion(version -> version.id().equals(id));
    }

    public Optional<Version> getVersion(final Predicate<Version> predicate) {
        for (final Version version : this.getVersions()) {
            if (predicate.test(version)) {
                return Optional.of(version);
            }
        }

        return Optional.empty();
    }

    public ReleaseManifest getReleaseManifest(final Version version) throws IOException {
        return this.launcherMeta.getReleaseManifest(version);
    }
}
