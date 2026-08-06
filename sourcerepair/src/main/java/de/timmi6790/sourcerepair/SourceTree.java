package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up the source of a type by name.
 *
 * <p>Some defects can only be recognised by reading the type an expression refers to rather than the
 * file the error was reported in. Compiler diagnostics name types in full, and a decompiler writes
 * every type to the path its package implies, so a name is enough to find the file.
 *
 * <p>Reads are cached because the same handful of types tends to be consulted once per diagnostic.
 */
final class SourceTree {

    private final Path root;
    private final Map<String, Optional<String>> cache = new HashMap<>();

    SourceTree(final Path root) {
        this.root = root;
    }

    /**
     * Reads the source that declares a type.
     *
     * <p>A nested type lives in the file of its outermost enclosing type, which is found by dropping
     * trailing name segments until a file exists.
     *
     * @param qualifiedName fully qualified type name as the compiler prints it
     * @return the file contents, or empty if the tree does not declare the type
     */
    Optional<String> read(final String qualifiedName) {
        return this.cache.computeIfAbsent(qualifiedName, this::load);
    }

    private Optional<String> load(final String qualifiedName) {
        String candidate = qualifiedName;
        while (!candidate.isEmpty()) {
            final Path file = this.root.resolve(candidate.replace('.', '/') + ".java");
            if (Files.isRegularFile(file)) {
                try {
                    return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
                } catch (final IOException exception) {
                    return Optional.empty();
                }
            }

            final int lastSegment = candidate.lastIndexOf('.');
            if (lastSegment < 0) {
                return Optional.empty();
            }
            candidate = candidate.substring(0, lastSegment);
        }
        return Optional.empty();
    }
}
