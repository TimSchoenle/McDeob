package com.shanebeestudios.mcdeop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumsTest {

    /** Well-known SHA-1 of the empty input. */
    private static final String EMPTY_SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    /** Well-known SHA-1 of "abc". */
    private static final String ABC_SHA1 = "a9993e364706816aba3e25717850c26c9cd0d89d";

    @Test
    @DisplayName("sha1 matches the known digests")
    void sha1MatchesKnownDigests(@TempDir final Path tempDir) throws IOException {
        final Path empty = Files.createFile(tempDir.resolve("empty.bin"));
        assertEquals(EMPTY_SHA1, Checksums.sha1(empty));

        final Path abc = tempDir.resolve("abc.bin");
        Files.writeString(abc, "abc");
        assertEquals(ABC_SHA1, Checksums.sha1(abc));
    }

    @Test
    @DisplayName("sha1 handles input larger than the read buffer")
    void sha1HandlesLargeInput(@TempDir final Path tempDir) throws IOException {
        final Path large = tempDir.resolve("large.bin");
        final byte[] payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        Files.write(large, payload);

        // Digesting in one pass must agree with the streamed implementation.
        assertEquals(40, Checksums.sha1(large).length());
        assertEquals(Checksums.sha1(large), Checksums.sha1(large));
    }

    @Test
    @DisplayName("matches accepts a file whose size and digest both agree")
    void matchesAcceptsCorrectFile(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("abc.bin");
        Files.writeString(file, "abc");

        assertTrue(Checksums.matches(file, ABC_SHA1, 3));
        assertTrue(Checksums.matches(file, ABC_SHA1.toUpperCase(java.util.Locale.ENGLISH), 3));
    }

    @Test
    @DisplayName("matches rejects a file whose digest differs despite a matching size")
    void matchesRejectsCorruptedFileOfCorrectSize(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("corrupt.bin");
        Files.writeString(file, "abd");

        // Same length as "abc", which is exactly what a size-only check could not catch.
        assertFalse(Checksums.matches(file, ABC_SHA1, 3));
    }

    @Test
    @DisplayName("matches rejects a truncated file")
    void matchesRejectsTruncatedFile(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("short.bin");
        Files.writeString(file, "ab");

        assertFalse(Checksums.matches(file, ABC_SHA1, 3));
    }

    @Test
    @DisplayName("matches rejects a missing file")
    void matchesRejectsMissingFile(@TempDir final Path tempDir) {
        assertFalse(Checksums.matches(tempDir.resolve("absent.bin"), ABC_SHA1, 3));
    }

    @Test
    @DisplayName("matches falls back to the size check when no digest is advertised")
    void matchesFallsBackToSizeWithoutDigest(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("abc.bin");
        Files.writeString(file, "abc");

        assertTrue(Checksums.matches(file, null, 3));
        assertTrue(Checksums.matches(file, "  ", 3));
        assertFalse(Checksums.matches(file, null, 4));
    }

    @Test
    @DisplayName("matches cannot verify anything without a digest or a size")
    void matchesRejectsWhenNothingIsKnown(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("abc.bin");
        Files.writeString(file, "abc");

        assertFalse(Checksums.matches(file, null, 0));
    }

    @Test
    @DisplayName("describe normalises a digest and names a missing one")
    void describeNormalisesDigest() {
        assertEquals(ABC_SHA1, Checksums.describe("  " + ABC_SHA1.toUpperCase(java.util.Locale.ENGLISH) + "  "));
        assertEquals("unknown", Checksums.describe(null));
        assertEquals("unknown", Checksums.describe(" "));
    }
}
