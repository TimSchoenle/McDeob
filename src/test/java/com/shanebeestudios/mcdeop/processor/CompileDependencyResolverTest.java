package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shanebeestudios.mcdeop.processor.CompileDependencyResolver.CompileDependencies;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileDependencyResolverTest {

    private static final LocalDate RELEASE_DATE = LocalDate.parse("2024-06-01");

    private final CompileDependencyResolver resolver = new CompileDependencyResolver();

    @Test
    @DisplayName("resolves a known annotation package to a Maven coordinate")
    void resolvesKnownAnnotationLibrary(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n" + "import org.jetbrains.annotations.Nullable;\n" + "class Foo {}\n");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(
                hasCoordinateStartingWith(dependencies, "org.jetbrains:annotations:"),
                "expected a jetbrains annotations coordinate, got " + dependencies.coordinates());
        assertTrue(dependencies.unresolvedPackages().isEmpty());
    }

    @Test
    @DisplayName("does not declare a dependency for a package the libraries already provide")
    void skipsPackagesProvidedByLibraries(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n" + "import com.google.common.collect.ImmutableList;\n" + "class Foo {}\n");
        writeJar(libraries.resolve("guava.jar"), "com/google/common/collect/ImmutableList.class");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(dependencies.coordinates().isEmpty());
        assertTrue(dependencies.unresolvedPackages().isEmpty());
    }

    @Test
    @DisplayName("does not declare a dependency for the sources' own packages")
    void skipsSelfDeclaredPackages(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(sources, "net/minecraft/core/Holder.java", "package net.minecraft.core;\nclass Holder {}\n");
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n" + "import net.minecraft.core.Holder;\n" + "class Foo {}\n");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(dependencies.unresolvedPackages().isEmpty());
    }

    @Test
    @DisplayName("treats JDK packages as provided without declaring anything")
    void ignoresJdkPackages(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n"
                        + "import java.util.List;\n"
                        + "import javax.crypto.Cipher;\n"
                        + "import javax.annotation.processing.Generated;\n"
                        + "class Foo {}\n");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(dependencies.coordinates().isEmpty(), "got " + dependencies.coordinates());
        assertTrue(dependencies.unresolvedPackages().isEmpty(), "got " + dependencies.unresolvedPackages());
    }

    @Test
    @DisplayName("reports a package that is neither provided nor known")
    void reportsUnknownPackage(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n" + "import com.example.unknown.Thing;\n" + "class Foo {}\n");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(dependencies.unresolvedPackages().contains("com.example.unknown"));
        assertFalse(dependencies.unresolvedPackages().isEmpty());
    }

    @Test
    @DisplayName("treats a jsr305 package as a dependency but not javax.annotation.processing")
    void distinguishesJsr305FromTheCompilerModule(@TempDir final Path tempDir) throws IOException {
        final Path sources = tempDir.resolve("decompiled");
        final Path libraries = Files.createDirectories(tempDir.resolve("libraries"));
        writeSource(
                sources,
                "net/minecraft/Foo.java",
                "package net.minecraft;\n" + "import javax.annotation.Nonnull;\n" + "class Foo {}\n");

        final CompileDependencies dependencies = this.resolver.resolve(sources, libraries, RELEASE_DATE);

        assertTrue(
                hasCoordinateStartingWith(dependencies, "com.google.code.findbugs:jsr305:"),
                "expected a jsr305 coordinate, got " + dependencies.coordinates());
    }

    private static boolean hasCoordinateStartingWith(final CompileDependencies dependencies, final String prefix) {
        for (final String coordinate : dependencies.coordinates()) {
            if (coordinate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void writeSource(final Path root, final String relativePath, final String content)
            throws IOException {
        final Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void writeJar(final Path jar, final String... entryNames) throws IOException {
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
