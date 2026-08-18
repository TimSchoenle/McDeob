package com.shanebeestudios.mcdeop.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUtil {
    public static void remove(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path directory, final IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void zip(final Path sourceDirPath, final Path zipFilePath) throws IOException {
        final Path parent = zipFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (final OutputStream output = Files.newOutputStream(zipFilePath);
                final ZipOutputStream zipStream = new ZipOutputStream(output)) {
            Files.walkFileTree(sourceDirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
                        throws IOException {
                    zipStream.putNextEntry(new ZipEntry(toEntryName(sourceDirPath, file)));
                    Files.copy(file, zipStream);
                    zipStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Builds the archive entry name for a file.
     *
     * <p>The ZIP format mandates {@code /} as the separator regardless of platform, and {@link
     * ZipOutputStream} does not normalise it. Relying on {@link Path#toString()} alone therefore
     * produced backslash-separated entries on Windows, which most tools unpack as a flat directory of
     * literally-named files rather than a tree.
     *
     * @param sourceDirPath root the entry is relative to
     * @param file the file being archived
     * @return the entry name with {@code /} separators
     */
    private static String toEntryName(final Path sourceDirPath, final Path file) {
        return sourceDirPath.relativize(file).toString().replace('\\', '/');
    }
}
