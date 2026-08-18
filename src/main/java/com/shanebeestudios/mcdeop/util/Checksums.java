package com.shanebeestudios.mcdeop.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * SHA-1 digests for verifying downloaded files against the launcher manifest.
 *
 * <p>SHA-1 is the algorithm Mojang publishes, so it is what can be checked against. This guards
 * against truncated, corrupted, and partially written files; it is not a defence against a hostile
 * server, for which SHA-1 would be too weak.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Checksums {
    private static final String ALGORITHM = "SHA-1";
    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Computes the SHA-1 digest of a file.
     *
     * @param path file to digest
     * @return the digest as lowercase hex
     * @throws IOException if the file cannot be read
     */
    public static String sha1(final Path path) throws IOException {
        final MessageDigest digest = newDigest();
        final byte[] buffer = new byte[BUFFER_SIZE];

        try (final InputStream input = Files.newInputStream(path);
                final DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            while (digestInput.read(buffer) != -1) {
                // Reading is what feeds the digest; the bytes themselves are not needed.
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Checks an existing file against the size and digest the manifest advertises.
     *
     * <p>Size is compared first because it is far cheaper and rules out most mismatches without
     * reading the file. A missing digest falls back to the size check alone, which is all the older
     * manifests support.
     *
     * @param path file to check, need not exist
     * @param expectedSha1 expected digest, or {@code null} when the manifest does not provide one
     * @param expectedSize expected size in bytes, or a non-positive value when unknown
     * @return {@code true} if the file is present and matches
     */
    public static boolean matches(final Path path, @Nullable final String expectedSha1, final long expectedSize) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        try {
            if (expectedSize > 0 && Files.size(path) != expectedSize) {
                return false;
            }
            if (expectedSha1 == null || expectedSha1.isBlank()) {
                // Nothing further to verify against; the size check above is the best available.
                return expectedSize > 0;
            }
            return sha1(path).equalsIgnoreCase(expectedSha1.trim());
        } catch (final IOException exception) {
            return false;
        }
    }

    /**
     * Normalises a digest for display and comparison.
     *
     * @param sha1 digest to normalise, may be {@code null}
     * @return the trimmed lowercase digest, or {@code "unknown"} when absent
     */
    public static String describe(@Nullable final String sha1) {
        return sha1 == null || sha1.isBlank() ? "unknown" : sha1.trim().toLowerCase(Locale.ENGLISH);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (final NoSuchAlgorithmException exception) {
            // Every conformant Java runtime ships SHA-1.
            throw new IllegalStateException(ALGORITHM + " is not available", exception);
        }
    }
}
