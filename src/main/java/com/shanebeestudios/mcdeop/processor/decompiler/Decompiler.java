package com.shanebeestudios.mcdeop.processor.decompiler;

import java.nio.file.Path;
import java.util.List;

public interface Decompiler {
    /**
     * Decompiles a jar into source files.
     *
     * @param jarPath jar to decompile
     * @param outputDir directory to write the sources to
     * @param libraries jars to resolve references against, empty when none are available or the
     *     backend does not {@linkplain #supportsExternalLibraries() support them}
     */
    void decompile(Path jarPath, Path outputDir, List<Path> libraries);

    /** Whether {@link #decompile} makes use of the libraries it is given. */
    default boolean supportsExternalLibraries() {
        return false;
    }

    /** Releases whatever the backend holds onto after a run. Backends without state need not override. */
    default void cleanup() {
        // No cleanup required by default.
    }
}
