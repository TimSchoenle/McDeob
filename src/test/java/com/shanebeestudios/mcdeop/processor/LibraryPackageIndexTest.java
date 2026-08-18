package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryPackageIndexTest {

    private final LibraryPackageIndex index = new LibraryPackageIndex();

    @Test
    @DisplayName("indexes the packages of a plain library jar")
    void indexesPlainJar(@TempDir final Path root) throws IOException {
        writeJar(root.resolve("guava.jar"), "com/google/common/collect/ImmutableList.class");

        final Set<String> packages = this.index.index(root);

        assertTrue(packages.contains("com.google.common.collect"));
    }

    @Test
    @DisplayName("rebases multi-release entries onto their real package")
    void rebasesMultiReleaseEntries(@TempDir final Path root) throws IOException {
        writeJar(root.resolve("mr.jar"), "META-INF/versions/9/com/example/Versioned.class");

        final Set<String> packages = this.index.index(root);

        assertTrue(packages.contains("com.example"), "versioned entry should index as its real package");
        assertFalse(
                packages.contains("META-INF.versions.9.com.example"),
                "the version directory must not become part of the package name");
    }

    @Test
    @DisplayName("skips natives jars, which are runtime only")
    void skipsNativesJars(@TempDir final Path root) throws IOException {
        writeJar(root.resolve("lwjgl-natives-windows.jar"), "org/lwjgl/native/Stub.class");

        assertTrue(this.index.index(root).isEmpty());
    }

    @Test
    @DisplayName("ignores module-info, which declares no package")
    void ignoresModuleInfo(@TempDir final Path root) throws IOException {
        writeJar(root.resolve("modular.jar"), "module-info.class");

        assertTrue(this.index.index(root).isEmpty());
    }

    @Test
    @DisplayName("ignores non-class entries")
    void ignoresNonClassEntries(@TempDir final Path root) throws IOException {
        writeJar(root.resolve("resources.jar"), "assets/minecraft/lang/en_us.json");

        assertTrue(this.index.index(root).isEmpty());
    }

    @Test
    @DisplayName("skips a damaged archive without failing the whole index")
    void skipsDamagedArchive(@TempDir final Path root) throws IOException {
        Files.writeString(root.resolve("broken.jar"), "this is not a zip archive");
        writeJar(root.resolve("good.jar"), "com/example/Good.class");

        final Set<String> packages = this.index.index(root);

        assertTrue(packages.contains("com.example"), "a broken jar must not stop the others from indexing");
    }

    @Test
    @DisplayName("returns nothing for a directory that does not exist")
    void handlesMissingDirectory(@TempDir final Path root) throws IOException {
        assertTrue(this.index.index(root.resolve("absent")).isEmpty());
    }

    private static void writeJar(final Path jar, final String... entryNames) throws IOException {
        Files.createDirectories(jar.getParent());
        try (final OutputStream output = Files.newOutputStream(jar);
                final ZipOutputStream zipStream = new ZipOutputStream(output)) {
            for (final String entryName : entryNames) {
                zipStream.putNextEntry(new ZipEntry(entryName));
                zipStream.write("stub".getBytes(StandardCharsets.UTF_8));
                zipStream.closeEntry();
            }
        }
    }
}
