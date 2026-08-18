package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shanebeestudios.mcdeop.processor.AnnotationLibrary.Version;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnnotationLibraryTest {

    private static final AnnotationLibrary LIBRARY = new AnnotationLibrary(
            "org.example:annotations",
            Set.of("org.example.annotations"),
            true,
            List.of(
                    new Version(LocalDate.parse("2024-01-01"), "3.0.0"),
                    new Version(LocalDate.parse("2022-01-01"), "2.0.0"),
                    new Version(LocalDate.parse("2020-01-01"), "1.0.0")));

    @Test
    @DisplayName("picks the newest version that already existed at release time")
    void picksContemporaryVersion() {
        assertEquals("3.0.0", LIBRARY.versionFor(LocalDate.parse("2025-06-01")));
        assertEquals("2.0.0", LIBRARY.versionFor(LocalDate.parse("2023-06-01")));
        assertEquals("1.0.0", LIBRARY.versionFor(LocalDate.parse("2021-06-01")));
    }

    @Test
    @DisplayName("treats a release on the publication date itself as eligible")
    void includesTheBoundaryDate() {
        assertEquals("2.0.0", LIBRARY.versionFor(LocalDate.parse("2022-01-01")));
    }

    @Test
    @DisplayName("falls back to the oldest version for releases predating them all")
    void fallsBackToOldestVersion() {
        assertEquals("1.0.0", LIBRARY.versionFor(LocalDate.parse("2010-01-01")));
    }

    @Test
    @DisplayName("builds a full coordinate for the release date")
    void buildsCoordinate() {
        assertEquals("org.example:annotations:2.0.0", LIBRARY.coordinateFor(LocalDate.parse("2023-06-01")));
    }

    @Test
    @DisplayName("matches sub-packages only when configured to")
    void matchesSubPackagesWhenEnabled() {
        assertTrue(LIBRARY.provides("org.example.annotations"));
        assertTrue(LIBRARY.provides("org.example.annotations.nested"));
        assertFalse(LIBRARY.provides("org.example.annotationsExtra"));
        assertFalse(LIBRARY.provides("org.other"));

        final AnnotationLibrary exact = new AnnotationLibrary(
                "org.example:exact",
                Set.of("javax.annotation"),
                false,
                List.of(new Version(LocalDate.parse("2020-01-01"), "1.0.0")));

        assertTrue(exact.provides("javax.annotation"));
        assertFalse(exact.provides("javax.annotation.processing"));
    }

    @Test
    @DisplayName("rejects a library declaring no packages or no versions")
    void rejectsIncompleteDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnnotationLibrary(
                        "org.example:empty",
                        Set.of(),
                        false,
                        List.of(new Version(LocalDate.parse("2020-01-01"), "1.0.0"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AnnotationLibrary("org.example:empty", Set.of("org.example"), false, List.of()));
    }

    @Test
    @DisplayName("the registry resolves the packages it claims and nothing else")
    void registryResolvesKnownPackages() {
        assertNotNull(AnnotationLibraryRegistry.findByPackage("org.jetbrains.annotations"));
        assertNotNull(AnnotationLibraryRegistry.findByPackage("org.jetbrains.annotations.ApiStatus"));
        assertNotNull(AnnotationLibraryRegistry.findByPackage("javax.annotation"));
        assertNotNull(AnnotationLibraryRegistry.findByPackage("org.jspecify.annotations"));

        // javax.annotation.processing belongs to the java.compiler module and must not pull in jsr305.
        assertNull(AnnotationLibraryRegistry.findByPackage("javax.annotation.processing"));
        assertNull(AnnotationLibraryRegistry.findByPackage("net.minecraft.core"));
    }

    @Test
    @DisplayName("jspecify picks the package layout matching the release date")
    void jspecifyVersionMatchesPackageLayout() {
        final AnnotationLibrary jspecify = AnnotationLibraryRegistry.findByPackage("org.jspecify.nullness");
        assertNotNull(jspecify);

        // Before 1.0.0 the annotations lived in org.jspecify.nullness, so an old release needs an old version.
        assertEquals("0.3.0", jspecify.versionFor(LocalDate.parse("2023-06-01")));
    }
}
