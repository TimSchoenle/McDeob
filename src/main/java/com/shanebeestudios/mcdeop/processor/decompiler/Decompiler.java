package com.shanebeestudios.mcdeop.processor.decompiler;

import java.nio.file.Path;
import java.util.List;

public interface Decompiler {
    void decompile(Path jarPath, Path outputDir);

    default void decompile(final Path jarPath, final Path outputDir, final List<Path> libraries) {
        this.decompile(jarPath, outputDir);
    }

    default boolean supportsExternalLibraries() {
        return false;
    }
}
