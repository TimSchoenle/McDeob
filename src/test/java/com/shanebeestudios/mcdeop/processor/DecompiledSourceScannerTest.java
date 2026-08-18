package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompiledSourceScannerTest {

    private final DecompiledSourceScanner scanner = new DecompiledSourceScanner();

    @Test
    @DisplayName("collects imported packages and derives declared ones from the layout")
    void collectsImportsAndDeclarations(@TempDir final Path root) throws IOException {
        writeSource(root, "net/minecraft/Foo.java", """
                package net.minecraft;

                import org.jetbrains.annotations.Nullable;
                import com.google.common.collect.ImmutableList;

                public class Foo {}
                """);

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertTrue(result.importedPackages().contains("org.jetbrains.annotations"));
        assertTrue(result.importedPackages().contains("com.google.common.collect"));
        assertTrue(result.declaredPackages().contains("net.minecraft"));
    }

    @Test
    @DisplayName("resolves a nested type reference down to its package")
    void resolvesNestedTypeToPackage(@TempDir final Path root) throws IOException {
        writeSource(root, "net/minecraft/Nested.java", """
                package net.minecraft;

                import org.jetbrains.annotations.ApiStatus.Internal;

                class Nested {}
                """);

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        // Only the lowercase leading segments form the package; ApiStatus.Internal is a type.
        assertTrue(result.importedPackages().contains("org.jetbrains.annotations"));
        assertFalse(result.importedPackages().contains("org.jetbrains.annotations.ApiStatus"));
    }

    @Test
    @DisplayName("handles static and wildcard imports")
    void handlesStaticAndWildcardImports(@TempDir final Path root) throws IOException {
        writeSource(root, "pkg/Types.java", """
                package pkg;

                import static java.util.Objects.requireNonNull;
                import java.util.function.*;

                class Types {}
                """);

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertTrue(result.importedPackages().contains("java.util"));
        assertTrue(result.importedPackages().contains("java.util.function"));
    }

    @Test
    @DisplayName("does not mistake a decompiler banner for the end of the header")
    void ignoresCommentBanners(@TempDir final Path root) throws IOException {
        writeSource(root, "pkg/Banner.java", """
                /*
                 * Decompiled with a decompiler from .class files.
                 */
                package pkg;

                import org.example.api.Service;

                class Banner {}
                """);

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertTrue(result.importedPackages().contains("org.example.api"), "imports after a banner must be seen");
    }

    @Test
    @DisplayName("stops reading at the first type declaration")
    void stopsAtTypeDeclaration(@TempDir final Path root) throws IOException {
        writeSource(root, "pkg/Stop.java", """
                package pkg;

                import org.example.before.Used;

                class Stop {
                    // import org.example.after.Ignored; would not be a real import anyway
                }
                """);

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertTrue(result.importedPackages().contains("org.example.before"));
        assertFalse(result.importedPackages().contains("org.example.after"));
    }

    @Test
    @DisplayName("reports the default package for a source at the root")
    void reportsDefaultPackageAtRoot(@TempDir final Path root) throws IOException {
        writeSource(root, "Root.java", "class Root {}\n");

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertTrue(result.declaredPackages().contains(""));
    }

    @Test
    @DisplayName("ignores files that are not Java sources")
    void ignoresNonJavaFiles(@TempDir final Path root) throws IOException {
        Files.createDirectories(root.resolve("assets"));
        Files.writeString(root.resolve("assets").resolve("pack.mcmeta"), "import org.example.Fake;");

        final DecompiledSourceScanner.ScanResult result = this.scanner.scan(root);

        assertEquals(0, result.importedPackages().size());
        assertEquals(0, result.declaredPackages().size());
    }

    private static void writeSource(final Path root, final String relativePath, final String content)
            throws IOException {
        final Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
