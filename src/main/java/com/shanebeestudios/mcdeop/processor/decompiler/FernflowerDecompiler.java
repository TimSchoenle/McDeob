package com.shanebeestudios.mcdeop.processor.decompiler;

import com.shanebeestudios.mcdeop.util.NativeImageUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

public class FernflowerDecompiler implements Decompiler {
    /**
     * Reading the JDK's type hierarchy yields better output, but the runtime scan needs the {@code jrt} filesystem
     * provider that native images do not ship.
     */
    private static String includeRuntimeOption() {
        return NativeImageUtil.isNativeImage() ? "--include-runtime=0" : "--include-runtime=1";
    }

    @Override
    public void decompile(final Path jarPath, final Path outputDir) {
        this.decompile(jarPath, outputDir, List.of());
    }

    @Override
    public void decompile(final Path jarPath, final Path outputDir, final List<Path> libraries) {
        final List<String> args = new ArrayList<>(libraries.size() + 3);
        args.add(includeRuntimeOption());
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
