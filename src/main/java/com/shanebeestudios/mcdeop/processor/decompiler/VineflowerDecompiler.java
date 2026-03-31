package com.shanebeestudios.mcdeop.processor.decompiler;

import java.nio.file.Path;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

public class VineflowerDecompiler implements Decompiler {
    @Override
    public void decompile(final Path jarPath, final Path outputDir) {
        // Setup FernFlower to properly decompile the jar file
        final String[] args = {
            "--ascii-strings=1", // Encode non-ASCII characters in string and character literals as Unicode escapes
            "--ternary-constant-simplification=1", // Simplify boolean constants in ternary operations
            "-jvn=1", // Use jad local variable naming
            "-jpr=1", // Use jad parameter variable naming
            "--include-runtime=0", // Native image doesn't expose the jrt filesystem provider
            jarPath.toAbsolutePath().toString(),
            outputDir.toAbsolutePath().toString()
        };

        ConsoleDecompiler.main(args);
    }
}
