package com.shanebeestudios.mcdeop.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Extraction of the archive formats McDeob downloads: jar/zip artifacts and {@code tar.gz} Java runtimes. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ArchiveUtil {
    /** The owner-execute bit of a POSIX mode, as tar archives store it. */
    private static final int OWNER_EXECUTE_BIT = 0100;

    /**
     * Extracts an archive, choosing the reader from the file name.
     *
     * @param archive a {@code .zip}/{@code .jar} or {@code .tar.gz}/{@code .tgz} file
     * @param targetDir directory to extract into; created if missing
     * @throws IOException if the format is unknown or extraction fails
     */
    public static void extract(final Path archive, final Path targetDir) throws IOException {
        final String fileName = archive.getFileName().toString().toLowerCase(Locale.ENGLISH);
        if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
            unzip(archive, targetDir);
        } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            untarGz(archive, targetDir);
        } else {
            throw new IOException("Unsupported archive format: " + archive.getFileName());
        }
    }

    public static void unzip(final Path archive, final Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (final ZipInputStream input = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                final Path target = resolveEntry(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    writeEntry(input, target);
                }
            }
        }
    }

    private static void untarGz(final Path archive, final Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (final TarArchiveInputStream input = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(archive))))) {
            TarArchiveEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                final Path target = resolveEntry(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (entry.isSymbolicLink()) {
                    createSymbolicLink(target, entry.getLinkName());
                    continue;
                }

                writeEntry(input, target);
                if ((entry.getMode() & OWNER_EXECUTE_BIT) != 0) {
                    makeExecutable(target);
                }
            }
        }
    }

    /**
     * Grants execute permission to whoever can already read the file.
     *
     * <p>Extracted Java launchers are useless without it, and the bit survives neither zip extraction nor a
     * {@link Files#copy} on POSIX systems.
     *
     * @param path file to mark executable; ignored on filesystems without POSIX permissions
     */
    public static void makeExecutable(final Path path) throws IOException {
        final PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }

        final Set<PosixFilePermission> permissions =
                new HashSet<>(view.readAttributes().permissions());
        if (permissions.contains(PosixFilePermission.OWNER_READ)) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if (permissions.contains(PosixFilePermission.GROUP_READ)) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        view.setPermissions(permissions);
    }

    /**
     * Recreates a symbolic link from a tar entry.
     *
     * <p>Creating one is not always permitted, notably on Windows without developer mode, so a failure is
     * recorded and extraction continues: the archives McDeob extracts use links for aliases only.
     *
     * @param link the path of the link itself
     * @param target the path the link points at, as stored in the archive
     */
    private static void createSymbolicLink(final Path link, final String target) throws IOException {
        final Path parent = link.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(link);

        try {
            Files.createSymbolicLink(link, link.getFileSystem().getPath(target));
        } catch (final IOException | UnsupportedOperationException exception) {
            log.debug("Could not recreate the symbolic link {} -> {}: {}", link, target, exception.getMessage());
        }
    }

    private static void writeEntry(final InputStream input, final Path target) throws IOException {
        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Resolves an archive entry name against the extraction root, rejecting names that escape it.
     *
     * @param targetDir extraction root
     * @param entryName entry name as stored in the archive
     * @return the absolute path to write to
     * @throws IOException if the entry would be written outside {@code targetDir}
     */
    private static Path resolveEntry(final Path targetDir, final String entryName) throws IOException {
        final Path root = targetDir.toAbsolutePath().normalize();
        final Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Archive entry escapes the extraction directory: " + entryName);
        }
        return resolved;
    }
}
