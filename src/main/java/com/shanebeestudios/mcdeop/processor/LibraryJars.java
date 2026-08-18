package com.shanebeestudios.mcdeop.processor;

import java.nio.file.Path;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shared rules for classifying downloaded library jars.
 *
 * <p>The split between compile-visible jars and platform natives is applied in three places: when
 * handing libraries to the decompiler, when indexing which packages the libraries provide, and in the
 * dependency block of the generated Gradle project. Keeping the rule and the glob in one place stops
 * those from drifting apart.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LibraryJars {
    static final String JAR_SUFFIX = ".jar";

    /**
     * Marks platform natives, which belong on the runtime classpath only. They carry no classes worth
     * compiling against and would otherwise pollute the package index.
     */
    static final String NATIVES_MARKER = "-natives-";

    /** Ant-style glob matching {@link #NATIVES_MARKER}, for the generated build script. */
    static final String NATIVES_GLOB = "**/*-natives-*.jar";

    /** Ant-style glob matching every jar, for the generated build script. */
    static final String ALL_JARS_GLOB = "**/*.jar";

    /**
     * Whether a file is a library jar that belongs on the compile classpath.
     *
     * @param file candidate file
     * @return {@code true} for jars that are not platform natives
     */
    static boolean isCompileVisible(final Path file) {
        final String fileName = file.getFileName().toString().toLowerCase(Locale.ENGLISH);
        return fileName.endsWith(JAR_SUFFIX) && !fileName.contains(NATIVES_MARKER);
    }
}
