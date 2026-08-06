package com.shanebeestudios.mcdeop.processor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;

/**
 * Indexes the packages provided by the downloaded Minecraft libraries.
 *
 * <p>Only the archive directories are read, not the entries themselves, so indexing stays cheap even
 * across the full library set.
 */
@Slf4j
final class LibraryPackageIndex {

    private static final String JAR_SUFFIX = ".jar";
    private static final String CLASS_SUFFIX = ".class";

    /**
     * Marks platform natives, which the generated project puts on the runtime classpath only. They
     * are excluded here so the index reflects what is actually visible at compile time.
     */
    private static final String NATIVES_MARKER = "-natives-";

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
                if (isCompileVisibleJar(file)) {
                    indexJar(file, packages);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return packages;
    }

    /**
     * Checks whether a file is a library jar that ends up on the compile classpath.
     *
     * @param file candidate file
     * @return {@code true} for non-natives jars
     */
    private static boolean isCompileVisibleJar(final Path file) {
        final String fileName = file.getFileName().toString().toLowerCase(Locale.ENGLISH);
        return fileName.endsWith(JAR_SUFFIX) && !fileName.contains(NATIVES_MARKER);
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

                final String name = entry.getName();
                if (!name.endsWith(CLASS_SUFFIX)) {
                    continue;
                }

                final int lastSeparator = name.lastIndexOf('/');
                if (lastSeparator > 0) {
                    target.add(name.substring(0, lastSeparator).replace('/', '.'));
                }
            }
        } catch (final IOException exception) {
            log.warn("Failed to index library jar {}; its packages may be reported as unresolved.", file, exception);
        }
    }
}
