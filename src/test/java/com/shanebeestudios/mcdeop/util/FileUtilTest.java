package com.shanebeestudios.mcdeop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilTest {

    @Test
    @DisplayName("zip entries always use forward slashes, whatever the platform separator is")
    void zipUsesForwardSlashSeparators(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("sources");
        final Path nested = sources.resolve("net").resolve("minecraft");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("Foo.java"), "class Foo {}");
        Files.writeString(sources.resolve("Root.java"), "class Root {}");

        final Path archive = tempDir.resolve("out.zip");
        FileUtil.zip(sources, archive);

        final List<String> entryNames = entryNamesOf(archive);
        assertTrue(entryNames.contains("net/minecraft/Foo.java"), "expected a slash-separated nested entry");
        assertTrue(entryNames.contains("Root.java"), "expected the root entry");
        for (final String name : entryNames) {
            assertFalse(name.contains("\\"), "entry name must not contain a backslash: " + name);
        }
    }

    @Test
    @DisplayName("zip writes one entry per file and no directory entries")
    void zipSkipsDirectories(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("sources");
        Files.createDirectories(sources.resolve("empty"));
        Files.createDirectories(sources.resolve("pkg"));
        Files.writeString(sources.resolve("pkg").resolve("A.java"), "class A {}");

        final Path archive = tempDir.resolve("out.zip");
        FileUtil.zip(sources, archive);

        assertEquals(List.of("pkg/A.java"), entryNamesOf(archive));
    }

    @Test
    @DisplayName("remove deletes a directory tree and tolerates a missing path")
    void removeDeletesRecursively(@TempDir final Path tempDir) throws IOException {
        final Path tree = tempDir.resolve("tree");
        Files.createDirectories(tree.resolve("a").resolve("b"));
        Files.writeString(tree.resolve("a").resolve("b").resolve("file.txt"), "content");

        FileUtil.remove(tree);
        assertFalse(Files.exists(tree));

        // A second call must not fail; callers use this to clear output that may not exist yet.
        FileUtil.remove(tree);
    }

    private static List<String> entryNamesOf(final Path archive) throws IOException {
        final List<String> names = new ArrayList<>();
        try (final ZipFile zipFile = new ZipFile(archive.toFile())) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }
}
