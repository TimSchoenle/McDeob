package com.shanebeestudios.mcdeop.processor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Determines which compile-time dependencies the generated project needs.
 *
 * <p>Rather than hardcoding a fixed set, the resolver compares what the decompiled sources import
 * against what the downloaded libraries provide. Whatever is left over is matched against
 * {@link AnnotationLibraryRegistry}; anything still unaccounted for is reported so a newly adopted
 * annotation library surfaces as a warning instead of a broken build.
 */
@Slf4j
final class CompileDependencyResolver {

    /**
     * Package roots owned by the JDK. Used only to keep the unresolved report free of noise, never
     * to decide on a dependency, so a stale entry cannot cause a wrong dependency to be emitted.
     *
     * <p>Deliberately string-based: {@code ModuleFinder.ofSystem()} reads the JDK image, which does
     * not exist in the GraalVM native image this tool ships as.
     */
    private static final Set<String> JDK_PACKAGE_ROOTS =
            Set.of("java", "jdk", "sun", "com.sun", "org.w3c", "org.xml", "org.ietf", "netscape");

    /** {@code javax} packages that look like the JDK but are published separately. */
    private static final Set<String> NON_JDK_JAVAX_ROOTS =
            Set.of("javax.annotation", "javax.inject", "javax.validation", "javax.servlet", "javax.persistence");

    /**
     * Packages nested below {@link #NON_JDK_JAVAX_ROOTS} that the JDK does own. {@code
     * javax.annotation} is split: the annotation types come from jsr305, while
     * {@code javax.annotation.processing} belongs to the {@code java.compiler} module.
     */
    private static final Set<String> JDK_JAVAX_EXCEPTIONS = Set.of("javax.annotation.processing");

    private final DecompiledSourceScanner sourceScanner;
    private final LibraryPackageIndex libraryPackageIndex;

    CompileDependencyResolver() {
        this.sourceScanner = new DecompiledSourceScanner();
        this.libraryPackageIndex = new LibraryPackageIndex();
    }

    /**
     * The compile-time dependencies a generated project needs.
     *
     * @param coordinates Maven coordinates to declare, in {@code group:artifact:version} form
     * @param unresolvedPackages imported packages that neither the libraries nor the registry cover
     */
    record CompileDependencies(List<String> coordinates, List<String> unresolvedPackages) {}

    /**
     * Resolves the compile-time dependencies for a decompiled Minecraft version.
     *
     * @param decompiledRoot directory containing the decompiled sources
     * @param librariesRoot directory containing the downloaded libraries
     * @param releaseDate release date of the Minecraft version, used to pick contemporary versions
     * @return the dependencies to declare and any packages left unresolved
     * @throws IOException if the sources or libraries cannot be read
     */
    CompileDependencies resolve(final Path decompiledRoot, final Path librariesRoot, final LocalDate releaseDate)
            throws IOException {
        final DecompiledSourceScanner.ScanResult sources = this.sourceScanner.scan(decompiledRoot);
        final Set<String> providedPackages = this.libraryPackageIndex.index(librariesRoot);

        final Set<String> coordinates = new LinkedHashSet<>();
        final List<String> unresolved = new ArrayList<>();

        final List<String> importedPackages = new ArrayList<>(sources.importedPackages());
        Collections.sort(importedPackages);

        for (final String importedPackage : importedPackages) {
            if (importedPackage.isEmpty()
                    || sources.declaredPackages().contains(importedPackage)
                    || providedPackages.contains(importedPackage)) {
                continue;
            }

            // The registry is consulted before the JDK heuristic so a known library always wins.
            final AnnotationLibrary library = AnnotationLibraryRegistry.findByPackage(importedPackage);
            if (library != null) {
                coordinates.add(library.coordinateFor(releaseDate));
                continue;
            }
            if (isJdkPackage(importedPackage)) {
                continue;
            }

            unresolved.add(importedPackage);
        }

        this.report(coordinates, unresolved);
        return new CompileDependencies(List.copyOf(coordinates), List.copyOf(unresolved));
    }

    /**
     * Logs the outcome so a missing mapping is visible without inspecting the generated project.
     *
     * @param coordinates resolved Maven coordinates
     * @param unresolved packages without a known source
     */
    private void report(final Set<String> coordinates, final List<String> unresolved) {
        if (coordinates.isEmpty()) {
            log.info("Decompiled sources need no additional compile-time dependencies.");
        } else {
            log.info("Resolved {} compile-time dependencies: {}", coordinates.size(), String.join(", ", coordinates));
        }

        if (!unresolved.isEmpty()) {
            log.warn(
                    "{} imported package(s) could not be resolved and are missing from the generated project: {}."
                            + " They are neither provided by the downloaded libraries nor known to McDeob;"
                            + " add the matching dependencies manually.",
                    unresolved.size(),
                    String.join(", ", unresolved));
        }
    }

    /**
     * Best-effort check for whether a package belongs to the JDK.
     *
     * @param packageName fully qualified package name
     * @return {@code true} if the package is most likely part of the JDK
     */
    private static boolean isJdkPackage(final String packageName) {
        for (final String root : JDK_PACKAGE_ROOTS) {
            if (packageName.equals(root) || packageName.startsWith(root + ".")) {
                return true;
            }
        }

        if (!packageName.startsWith("javax.")) {
            return false;
        }
        for (final String exception : JDK_JAVAX_EXCEPTIONS) {
            if (packageName.equals(exception) || packageName.startsWith(exception + ".")) {
                return true;
            }
        }
        for (final String root : NON_JDK_JAVAX_ROOTS) {
            if (packageName.equals(root) || packageName.startsWith(root + ".")) {
                return false;
            }
        }
        return true;
    }
}
