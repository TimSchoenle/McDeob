package com.shanebeestudios.mcdeop.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Collects the packages a decompiled source tree imports and declares.
 *
 * <p>Only the header of each file is read. Parsing stops at the first type declaration, so the bulk
 * of the sources is never touched.
 */
final class DecompiledSourceScanner {

    /** Guards against reading an entire file should a header never terminate as expected. */
    private static final int MAX_HEADER_LINES = 512;

    private static final String JAVA_SUFFIX = ".java";
    private static final String IMPORT_KEYWORD = "import ";
    private static final String STATIC_KEYWORD = "static ";

    /** Byte order mark, stripped so a marked file does not hide its own import block. */
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * Marks the end of the header. Anything else before the first type is skipped rather than
     * treated as a terminator, so unexpected content cannot silently suppress import detection.
     */
    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+\\w|\\b(?:class|interface|enum|record)$");

    /**
     * The packages a source tree imports and the packages it declares itself.
     *
     * @param importedPackages packages referenced by import statements
     * @param declaredPackages packages the tree defines, derived from the directory layout
     */
    record ScanResult(Set<String> importedPackages, Set<String> declaredPackages) {}

    /**
     * Scans a decompiled source tree.
     *
     * @param root directory containing the decompiled sources
     * @return the imported and declared packages
     * @throws IOException if the tree cannot be read
     */
    ScanResult scan(final Path root) throws IOException {
        final Set<String> imported = new HashSet<>();
        final Set<String> declared = new HashSet<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) throws IOException {
                if (!file.getFileName().toString().endsWith(JAVA_SUFFIX)) {
                    return FileVisitResult.CONTINUE;
                }

                declared.add(declaredPackageOf(root, file));
                collectImports(file, imported);
                return FileVisitResult.CONTINUE;
            }
        });

        return new ScanResult(imported, declared);
    }

    /**
     * Derives the package a source file belongs to from its location in the tree.
     *
     * <p>The directory layout is used rather than the {@code package} statement because decompiled
     * output is always written to matching directories, and it avoids a second parse.
     *
     * @param root root of the source tree
     * @param file the source file
     * @return the declared package, empty for the default package
     */
    private static String declaredPackageOf(final Path root, final Path file) {
        final Path parent = root.relativize(file).getParent();
        if (parent == null) {
            return "";
        }
        return parent.toString().replace('\\', '.').replace('/', '.');
    }

    /**
     * Reads the import statements from a file header.
     *
     * @param file the source file
     * @param target set collecting the imported packages
     * @throws IOException if the file cannot be read
     */
    private static void collectImports(final Path file, final Set<String> target) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            boolean inBlockComment = false;

            for (int lineNumber = 0; lineNumber < MAX_HEADER_LINES; lineNumber++) {
                final String rawLine = reader.readLine();
                if (rawLine == null) {
                    return;
                }

                final String line = (lineNumber == 0 ? stripByteOrderMark(rawLine) : rawLine).trim();
                if (inBlockComment) {
                    inBlockComment = !line.endsWith("*/");
                    continue;
                }
                if (line.startsWith("/*")) {
                    inBlockComment = !line.endsWith("*/");
                    continue;
                }
                // Skipped before the terminator check: decompiler banners mention ".class files"
                // and would otherwise read as a type declaration.
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                if (line.startsWith(IMPORT_KEYWORD)) {
                    final String importedPackage = packageOfImport(line);
                    if (!importedPackage.isEmpty()) {
                        target.add(importedPackage);
                    }
                    continue;
                }
                // The first type declaration ends the header. Everything before it, including the
                // package statement and any annotations preceding it in package-info files, is
                // skipped without assuming a fixed layout.
                if (TYPE_DECLARATION.matcher(line).find()) {
                    return;
                }
            }
        }
    }

    /**
     * Removes a leading byte order mark.
     *
     * @param line the first line of a file
     * @return the line without its byte order mark
     */
    private static String stripByteOrderMark(final String line) {
        return !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK ? line.substring(1) : line;
    }

    /**
     * Extracts the package name from a single import statement.
     *
     * @param line a trimmed line starting with {@code import}
     * @return the imported package, empty if the statement cannot be parsed
     */
    private static String packageOfImport(final String line) {
        String reference = line.substring(IMPORT_KEYWORD.length()).trim();
        if (reference.startsWith(STATIC_KEYWORD)) {
            reference = reference.substring(STATIC_KEYWORD.length()).trim();
        }

        final int terminator = reference.indexOf(';');
        if (terminator < 0) {
            return "";
        }
        reference = reference.substring(0, terminator).trim();
        if (reference.endsWith(".*")) {
            reference = reference.substring(0, reference.length() - 2);
        }

        return packagePrefixOf(reference);
    }

    /**
     * Takes the leading lowercase segments of a type reference, which form its package.
     *
     * <p>This resolves nested types and static members without a symbol table:
     * {@code org.jetbrains.annotations.ApiStatus.Internal} yields {@code org.jetbrains.annotations}.
     *
     * @param reference a fully qualified reference
     * @return the package portion
     */
    private static String packagePrefixOf(final String reference) {
        final StringBuilder builder = new StringBuilder(reference.length());

        for (final String segment : reference.split("\\.")) {
            if (segment.isEmpty() || !Character.isLowerCase(segment.charAt(0))) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(segment);
        }

        return builder.toString();
    }
}
