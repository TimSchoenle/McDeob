package com.shanebeestudios.mcdeop.processor.decompiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

public class VineflowerDecompiler implements Decompiler {
    private static final String[] STABILITY_OPTIONS = {
        "--ascii-strings=1", // Encode non-ASCII chars as escapes for consistent output
        "--ternary-constant-simplification=0", // Disable extra rewrites that can produce invalid output
        "--ternary-in-if=0", // Disable experimental ternary collapsing in if conditions
        "--verify-merges=1", // Prefer safer variable merge reconstruction over cleaner-looking output
        "--old-try-dedup=1", // Safer for obfuscated exception handlers
        "--include-runtime=0" // Native image doesn't expose the jrt filesystem provider
    };

    @Override
    public void decompile(final Path jarPath, final Path outputDir) {
        final List<String> args = new ArrayList<>(STABILITY_OPTIONS.length + 2);
        args.addAll(List.of(STABILITY_OPTIONS));
        args.add(jarPath.toAbsolutePath().toString());
        args.add(outputDir.toAbsolutePath().toString());

        ConsoleDecompiler.main(args.toArray(String[]::new));
    }
}
