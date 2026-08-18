package de.timmi6790.launchermeta.data.version;

import java.util.Locale;
import java.util.Optional;

public enum VersionType {
    RELEASE,
    SNAPSHOT,
    OLD_BETA,
    OLD_ALPHA;

    /**
     * Resolves the manifest's textual version type.
     *
     * <p>Returns empty rather than throwing for values this enum does not know. Mojang controls the
     * manifest and has added types before; propagating an exception here would fail the whole version
     * list over a single unrecognised entry.
     *
     * @param id type name as it appears in the manifest
     * @return the matching type, or empty when unrecognised
     */
    public static Optional<VersionType> fromId(final String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        final String normalized = id.trim().replace('-', '_').toUpperCase(Locale.ENGLISH);
        for (final VersionType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
