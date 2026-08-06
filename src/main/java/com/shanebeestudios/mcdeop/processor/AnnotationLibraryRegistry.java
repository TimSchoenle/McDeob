package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.AnnotationLibrary.Version;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Known annotation libraries that Minecraft references without shipping them.
 *
 * <p>The registry is deliberately limited to libraries the decompiled sources have actually been
 * observed to import. Packages outside it are reported as unresolved rather than guessed at, so a
 * future annotation library surfaces as an actionable warning instead of a silently broken project.
 *
 * <p>The newest version of each library comes from the version catalog so Renovate keeps it current.
 * Its {@code availableFrom} date is the publication date of the version that was current when the
 * entry was written; a Renovate bump leaves that date slightly stale, which is harmless because the
 * newest entry only ever applies to the newest Minecraft releases.
 */
final class AnnotationLibraryRegistry {

    private static final List<AnnotationLibrary> LIBRARIES = List.of(
            new AnnotationLibrary(
                    "org.jetbrains:annotations",
                    Set.of("org.jetbrains.annotations"),
                    true,
                    List.of(
                            new Version(LocalDate.parse("2025-01-22"), GeneratedConstant.JETBRAINS_ANNOTATIONS_VERSION),
                            new Version(LocalDate.parse("2024-09-25"), "25.0.0"),
                            new Version(LocalDate.parse("2023-11-15"), "24.1.0"),
                            new Version(LocalDate.parse("2023-01-11"), "24.0.0"),
                            new Version(LocalDate.parse("2022-12-08"), "23.1.0"),
                            new Version(LocalDate.parse("2021-11-10"), "23.0.0"),
                            new Version(LocalDate.parse("2021-08-12"), "22.0.0"),
                            new Version(LocalDate.parse("2021-05-25"), "21.0.1"),
                            new Version(LocalDate.parse("2020-09-02"), "20.1.0"),
                            new Version(LocalDate.parse("2020-02-14"), "19.0.0"),
                            new Version(LocalDate.parse("2019-11-07"), "18.0.0"),
                            new Version(LocalDate.parse("2019-01-30"), "17.0.0"),
                            new Version(LocalDate.parse("2018-09-18"), "16.0.3"),
                            new Version(LocalDate.parse("2015-10-15"), "15.0"),
                            new Version(LocalDate.parse("2013-12-17"), "13.0"))),
            // Matched exactly rather than by prefix: javax.annotation.processing belongs to the
            // java.compiler module and must not pull in jsr305.
            new AnnotationLibrary(
                    "com.google.code.findbugs:jsr305",
                    Set.of("javax.annotation", "javax.annotation.concurrent", "javax.annotation.meta"),
                    false,
                    List.of(
                            new Version(LocalDate.parse("2017-03-31"), GeneratedConstant.JSR305_VERSION),
                            new Version(LocalDate.parse("2015-10-09"), "3.0.1"),
                            new Version(LocalDate.parse("2014-07-10"), "3.0.0"),
                            new Version(LocalDate.parse("2013-12-31"), "2.0.3"),
                            new Version(LocalDate.parse("2012-01-09"), "2.0.0"),
                            new Version(LocalDate.parse("2009-08-24"), "1.3.9"))),
            // Normally shipped as a runtime library. Listed so releases that reference it without
            // shipping it still resolve, and because the date rule picks the version whose package
            // layout matches: org.jspecify.nullness before 1.0.0, org.jspecify.annotations after.
            new AnnotationLibrary(
                    "org.jspecify:jspecify",
                    Set.of("org.jspecify"),
                    true,
                    List.of(
                            new Version(LocalDate.parse("2024-07-16"), GeneratedConstant.JSPECIFY_VERSION),
                            new Version(LocalDate.parse("2022-12-13"), "0.3.0"),
                            new Version(LocalDate.parse("2021-07-21"), "0.2.0"))));

    private AnnotationLibraryRegistry() {}

    /**
     * Finds the library providing the given package.
     *
     * @param packageName fully qualified package name
     * @return the matching library, or {@code null} if the package is unknown
     */
    static AnnotationLibrary findByPackage(final String packageName) {
        for (final AnnotationLibrary library : LIBRARIES) {
            if (library.provides(packageName)) {
                return library;
            }
        }
        return null;
    }
}
