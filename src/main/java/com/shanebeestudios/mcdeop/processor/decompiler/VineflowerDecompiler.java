package com.shanebeestudios.mcdeop.processor.decompiler;

import com.shanebeestudios.mcdeop.util.NativeImageUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

public class VineflowerDecompiler implements Decompiler {
    /**
     * Options are pinned explicitly rather than left to Vineflower's defaults because the CLI silently ignores
     * unrecognised options: a renamed or removed option produces no diagnostic, only quietly different output.
     */
    private static final String[] DECOMPILER_OPTIONS = {
        "--ascii-strings=1", // Escape non-ASCII literals so invisible codepoints stay visible in the source
        "--ternary-constant-simplification=0", // Folding boolean ternary branches can emit invalid code
        "--ternary-in-if=1", // Recovers the original loop conditions instead of goto-style break chains
        "--verify-merges=0", // Troubleshooting-only switch; enabling it replaces real LVT names with varNN
        "--old-try-dedup=0", // Inserts dummy exception handlers that break pattern-matching switches
        "--pattern-matching=1", // Resugars `case Type t ->` switches over sealed hierarchies
        "--decompile-switch-expressions=1" // Resugars the SwitchBootstraps.typeSwitch invokedynamic
    };

    /**
     * Letting the decompiler read the JDK's type hierarchy removes redundant casts and restores {@code @Override}
     * annotations, but the runtime scan needs the {@code jrt} filesystem provider that native images do not ship.
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
        final List<String> args = new ArrayList<>(DECOMPILER_OPTIONS.length + libraries.size() + 3);
        args.addAll(List.of(DECOMPILER_OPTIONS));
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
