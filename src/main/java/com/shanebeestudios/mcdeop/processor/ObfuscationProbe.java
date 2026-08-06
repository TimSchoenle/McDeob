package com.shanebeestudios.mcdeop.processor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Decides whether a Minecraft jar still carries obfuscated names.
 *
 * <p>Needed because Mojang stopped both obfuscating the server and publishing mappings for it: from Minecraft 26.1
 * onwards the version manifest lists no {@code server_mappings}, and the jar ships readable names already. Without
 * this distinction a missing mappings file looks like a failure, when for those versions there is simply nothing
 * left to remap.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ObfuscationProbe {
    /**
     * Obfuscated Minecraft jars put nearly every class in the default package, while readable ones nest them at
     * least as deep as {@code net/minecraft/<area>}. The two populations sit at opposite ends of this ratio — 0.4%
     * for the last obfuscated release against 99.9% for the first readable one — so the midpoint is a safe cut.
     */
    private static final double READABLE_SHARE_THRESHOLD = 0.5;

    private static final int MINIMUM_PACKAGE_DEPTH = 3;
    private static final String CLASS_SUFFIX = ".class";
    private static final String METADATA_PREFIX = "META-INF/";

    /**
     * Samples a jar's class names.
     *
     * @param jar the jar to inspect
     * @return {@code true} if most classes sit in the default package, which only happens after obfuscation
     * @throws IOException if the jar cannot be read
     */
    static boolean looksObfuscated(final Path jar) throws IOException {
        int classes = 0;
        int readable = 0;

        try (final ZipFile zipFile = new ZipFile(jar.toFile())) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (!name.endsWith(CLASS_SUFFIX) || name.startsWith(METADATA_PREFIX)) {
                    continue;
                }

                classes++;
                if (packageDepthOf(name) >= MINIMUM_PACKAGE_DEPTH) {
                    readable++;
                }
            }
        }

        if (classes == 0) {
            return false;
        }
        return (double) readable / classes < READABLE_SHARE_THRESHOLD;
    }

    /**
     * @param entryName a class entry name such as {@code net/minecraft/world/Foo.class}
     * @return how many name segments it has, counting the class itself
     */
    private static int packageDepthOf(final String entryName) {
        int depth = 1;
        for (int index = 0; index < entryName.length(); index++) {
            if (entryName.charAt(index) == '/') {
                depth++;
            }
        }
        return depth;
    }
}
