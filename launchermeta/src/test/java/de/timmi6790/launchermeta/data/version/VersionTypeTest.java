package de.timmi6790.launchermeta.data.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionTypeTest {

    @ParameterizedTest
    @CsvSource({
        "release, RELEASE",
        "snapshot, SNAPSHOT",
        "old_beta, OLD_BETA",
        "old-beta, OLD_BETA",
        "old_alpha, OLD_ALPHA",
        "old-alpha, OLD_ALPHA",
        "RELEASE, RELEASE",
        "  release  , RELEASE",
    })
    @DisplayName("maps the manifest spelling to the matching constant")
    void mapsManifestSpelling(final String id, final VersionType expected) {
        assertEquals(expected, VersionType.fromId(id).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(VersionType.class)
    @DisplayName("every constant round-trips through its own name")
    void everyConstantRoundTrips(final VersionType type) {
        assertEquals(type, VersionType.fromId(type.name()).orElseThrow());
        assertEquals(
                type,
                VersionType.fromId(type.name().toLowerCase(Locale.ENGLISH)).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "experimental", "april_fools", "old_gamma"})
    @DisplayName("returns empty for a type it does not know instead of throwing")
    void returnsEmptyForUnknownType(final String id) {
        assertTrue(VersionType.fromId(id).isEmpty());
    }

    @Test
    @DisplayName("returns empty for null")
    void returnsEmptyForNull() {
        assertTrue(VersionType.fromId(null).isEmpty());
    }

    @Test
    @DisplayName("is unaffected by the default locale")
    void isLocaleIndependent() {
        final Locale original = Locale.getDefault();
        try {
            // Turkish uppercases 'i' to a dotted capital, which a locale-sensitive fold would break on.
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(VersionType.RELEASE, VersionType.fromId("release").orElseThrow());
            assertEquals(VersionType.SNAPSHOT, VersionType.fromId("snapshot").orElseThrow());
        } finally {
            Locale.setDefault(original);
        }
    }
}
