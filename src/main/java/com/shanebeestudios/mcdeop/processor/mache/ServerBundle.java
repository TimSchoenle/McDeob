package com.shanebeestudios.mcdeop.processor.mache;

import com.shanebeestudios.mcdeop.util.FileUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;

/**
 * The contents Mojang's server bundler jar carries: the real server jar and its exact compile classpath.
 *
 * <p>Read from the bundler rather than from the launcher manifest because the manifest lists the client's
 * libraries. mache builds the classpath from {@code META-INF/libraries.list}, and codebook's inference and
 * Vineflower's type resolution both depend on it being the same set.
 *
 * @param serverJar the extracted server jar
 * @param libraries the extracted library jars, in the order the bundler lists them
 */
@Slf4j
public record ServerBundle(Path serverJar, List<Path> libraries) {
    private static final String VERSIONS_LIST = "META-INF/versions.list";
    private static final String LIBRARIES_LIST = "META-INF/libraries.list";
    private static final String VERSIONS_PREFIX = "META-INF/versions/";
    private static final String LIBRARIES_PREFIX = "META-INF/libraries/";

    /** {@code libraries.list} and {@code versions.list} rows are {@code hash}, coordinates and path. */
    private static final int LIST_COLUMN_COUNT = 3;

    private static final int PATH_COLUMN = 2;

    public ServerBundle {
        libraries = List.copyOf(libraries);
    }

    /**
     * Unpacks a bundler jar.
     *
     * @param bundlerJar the jar Mojang publishes as the server download
     * @param serverJarTarget where the inner server jar is written
     * @param librariesDirectory where the library jars are written, keeping their Maven-style relative paths
     * @return the extracted contents
     * @throws IOException if the jar is not a bundler, or extraction fails
     */
    public static ServerBundle extract(final Path bundlerJar, final Path serverJarTarget, final Path librariesDirectory)
            throws IOException {
        try (final ZipFile zipFile = new ZipFile(bundlerJar.toFile())) {
            final List<String> versionRows = readList(zipFile, VERSIONS_LIST);
            if (versionRows.size() != 1) {
                throw new IOException(bundlerJar.getFileName() + " is not a Minecraft server bundler: expected one "
                        + VERSIONS_LIST + " entry, found " + versionRows.size());
            }

            final Path serverJar = extractEntry(
                    zipFile, VERSIONS_PREFIX + columnOf(versionRows.get(0), VERSIONS_LIST), serverJarTarget);

            final List<Path> libraries = new ArrayList<>();
            for (final String row : readList(zipFile, LIBRARIES_LIST)) {
                final String relativePath = columnOf(row, LIBRARIES_LIST);
                libraries.add(extractEntry(
                        zipFile, LIBRARIES_PREFIX + relativePath, librariesDirectory.resolve(relativePath)));
            }

            log.info("Extracted the server jar and {} bundled libraries.", libraries.size());
            Collections.sort(libraries);
            return new ServerBundle(serverJar, libraries);
        }
    }

    private static List<String> readList(final ZipFile zipFile, final String entryName) throws IOException {
        final ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IOException("Server bundler is missing " + entryName);
        }

        final List<String> rows = new ArrayList<>();
        try (final InputStream input = zipFile.getInputStream(entry)) {
            for (final String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank()) {
                    rows.add(line);
                }
            }
        }
        return rows;
    }

    private static String columnOf(final String row, final String entryName) throws IOException {
        final String[] columns = row.split("\\s+");
        if (columns.length != LIST_COLUMN_COUNT) {
            throw new IOException("Malformed " + entryName + " row: " + row);
        }
        return columns[PATH_COLUMN];
    }

    /**
     * Copies one entry out of the bundler.
     *
     * <p>Existing files are reused: the bundler's contents are fixed for a given Minecraft version, so a second
     * run can skip re-extracting hundreds of library jars.
     */
    private static Path extractEntry(final ZipFile zipFile, final String entryName, final Path target)
            throws IOException {
        final ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IOException("Server bundler is missing " + entryName);
        }

        if (Files.isRegularFile(target) && Files.size(target) == entry.getSize()) {
            return target;
        }

        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FileUtil.remove(target);
        try (final InputStream input = zipFile.getInputStream(entry)) {
            Files.copy(input, target);
        }
        return target;
    }
}
