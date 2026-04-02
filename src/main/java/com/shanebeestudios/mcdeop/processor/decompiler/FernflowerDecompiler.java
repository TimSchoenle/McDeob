package com.shanebeestudios.mcdeop.processor.decompiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

public class FernflowerDecompiler implements Decompiler {
    private static final String[] LEGACY_OPTIONS = {
        // Preserve native-image compatibility by avoiding runtime module probing.
        "--include-runtime=0"
    };

    @Override
    public void decompile(final Path jarPath, final Path outputDir) {
        this.decompile(jarPath, outputDir, List.of());
    }

    @Override
    public void decompile(final Path jarPath, final Path outputDir, final List<Path> libraries) {
        final List<String> args = new ArrayList<>(LEGACY_OPTIONS.length + libraries.size() + 2);
        args.addAll(List.of(LEGACY_OPTIONS));
        for (final Path library : libraries) {
            args.add("--add-external=" + library.toAbsolutePath());
        }
        args.add(jarPath.toAbsolutePath().toString());
        args.add(outputDir.toAbsolutePath().toString());

        ConsoleDecompiler.main(args.toArray(String[]::new));
    }

    @Override
    public boolean supportsExternalLibraries() {
        return true;
    }
}
