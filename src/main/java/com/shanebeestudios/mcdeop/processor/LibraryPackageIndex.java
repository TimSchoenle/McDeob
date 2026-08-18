package com.shanebeestudios.mcdeop.processor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Indexes the packages provided by the downloaded Minecraft libraries.
 *
 * <p>Only the archive directories are read, not the entries themselves, so indexing stays cheap even
 * across the full library set.
 */
@Slf4j
final class LibraryPackageIndex {

    private static final String CLASS_SUFFIX = ".class";

    /**
     * Prefix of the versioned entries in a multi-release jar. Their package is the part after the
     * version directory, so the prefix is stripped rather than being read as part of the name.
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html">JAR spec</a>
     */
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

    /** Carries no package of its own and would otherwise index as the default package. */
    private static final String MODULE_INFO = "module-info.class";

    /**
     * Indexes every compile-visible library jar below the given directory.
     *
     * @param root directory holding the downloaded libraries
     * @return packages provided by those libraries
     * @throws IOException if the directory cannot be walked
     */
    Set<String> index(final Path root) throws IOException {
        final Set<String> packages = new HashSet<>();
        if (!Files.isDirectory(root)) {
            return packages;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
                if (LibraryJars.isCompileVisible(file)) {
                    indexJar(file, packages);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return packages;
    }

    /**
     * Adds the packages of a single jar to the index.
     *
     * <p>A damaged archive is logged and skipped rather than failing project generation; the only
     * consequence is that a dependency may be reported as unresolved.
     *
     * @param file the jar to read
     * @param target set collecting the provided packages
     */
    private static void indexJar(final Path file, final Set<String> target) {
        try (final ZipFile archive = new ZipFile(file.toFile())) {
            final Enumeration<? extends ZipEntry> entries = archive.entries();

            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                final String packageName = packageOf(entry.getName());
                if (packageName != null) {
                    target.add(packageName);
                }
            }
        } catch (final IOException exception) {
            log.warn("Failed to index library jar {}; its packages may be reported as unresolved.", file, exception);
        }
    }

    /**
     * Derives the package a class entry belongs to.
     *
     * <p>Versioned entries of a multi-release jar are rebased onto their real package. Reading the
     * raw entry name instead would index {@code META-INF/versions/9/com/example/Foo.class} as package
     * {@code META-INF.versions.9.com.example}, leaving the actual package unindexed and its imports
     * reported as unresolved.
     *
     * @param entryName name of the archive entry
     * @return the package name, or {@code null} for entries that declare none
     */
    @Nullable private static String packageOf(final String entryName) {
        if (!entryName.endsWith(CLASS_SUFFIX)) {
            return null;
        }

        String name = entryName;
        if (name.startsWith(MULTI_RELEASE_PREFIX)) {
            final int versionEnd = name.indexOf('/', MULTI_RELEASE_PREFIX.length());
            if (versionEnd < 0) {
                return null;
            }
            name = name.substring(versionEnd + 1);
        }

        if (name.equals(MODULE_INFO)) {
            return null;
        }

        final int lastSeparator = name.lastIndexOf('/');
        return lastSeparator > 0 ? name.substring(0, lastSeparator).replace('/', '.') : null;
    }
}
