package com.shanebeestudios.mcdeop.processor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * An annotation library that Mojang compiles against but does not publish as a runtime library.
 *
 * <p>Annotations survive into the class files and therefore into the decompiled sources, but the
 * launcher manifest only lists what the game needs to <em>run</em>. The resulting types have to be
 * resolved separately for the generated project to compile.
 *
 * @param module Maven coordinate without a version, for example {@code org.jetbrains:annotations}
 * @param packages packages this library provides
 * @param matchSubPackages whether packages nested below {@link #packages} also belong to it
 * @param versions known versions, ordered newest first
 */
record AnnotationLibrary(String module, Set<String> packages, boolean matchSubPackages, List<Version> versions) {

    /**
     * A library version together with the date it became available on Maven Central.
     *
     * @param availableFrom publication date of {@link #version}
     * @param version the Maven version string
     */
    record Version(LocalDate availableFrom, String version) {}

    AnnotationLibrary {
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("Annotation library " + module + " declares no packages");
        }
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("Annotation library " + module + " declares no versions");
        }
    }

    /**
     * Checks whether this library provides the given package.
     *
     * @param packageName fully qualified package name
     * @return {@code true} if the package belongs to this library
     */
    boolean provides(final String packageName) {
        for (final String candidate : this.packages) {
            if (candidate.equals(packageName)) {
                return true;
            }
            if (this.matchSubPackages && packageName.startsWith(candidate + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects the newest version that already existed when the Minecraft version was released.
     *
     * <p>A contemporary version is the safest choice in both directions: it is guaranteed to contain
     * every annotation the bytecode of that era could reference, and it predates later renames such
     * as jspecify moving {@code org.jspecify.nullness} to {@code org.jspecify.annotations}. Picking
     * the newest version instead would break old releases that reference since-renamed packages,
     * while picking the oldest would miss annotations added over time.
     *
     * @param releaseDate release date of the Minecraft version
     * @return the matching version, or the oldest known version for releases predating them all
     */
    String versionFor(final LocalDate releaseDate) {
        String oldest = null;
        for (final Version candidate : this.versions) {
            if (!candidate.availableFrom().isAfter(releaseDate)) {
                return candidate.version();
            }
            oldest = candidate.version();
        }
        return oldest;
    }

    /**
     * Builds the full Maven coordinate for the given release date.
     *
     * @param releaseDate release date of the Minecraft version
     * @return coordinate in {@code group:artifact:version} form
     */
    String coordinateFor(final LocalDate releaseDate) {
        return this.module + ":" + this.versionFor(releaseDate);
    }
}
