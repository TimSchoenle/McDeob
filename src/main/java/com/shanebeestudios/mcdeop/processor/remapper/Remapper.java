package com.shanebeestudios.mcdeop.processor.remapper;

import java.nio.file.Path;

public interface Remapper {
    void remap(Path jarPath, Path mappingsPath, Path outputDir);

    /** Releases whatever the backend holds onto after a run. Backends without state need not override. */
    default void cleanup() {
        // No cleanup required by default.
    }
}
