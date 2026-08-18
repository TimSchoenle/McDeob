package com.shanebeestudios.mcdeop.processor.decompiler;

import java.util.List;

public class VineflowerDecompiler extends ConsoleEngineDecompiler {
    /**
     * Options are pinned explicitly rather than left to Vineflower's defaults because the CLI silently ignores
     * unrecognised options: a renamed or removed option produces no diagnostic, only quietly different output.
     */
    private static final List<String> DECOMPILER_OPTIONS = List.of(
            "--ascii-strings=1", // Escape non-ASCII literals so invisible codepoints stay visible in the source
            "--ternary-constant-simplification=0", // Folding boolean ternary branches can emit invalid code
            "--ternary-in-if=1", // Recovers the original loop conditions instead of goto-style break chains
            "--verify-merges=0", // Troubleshooting-only switch; enabling it replaces real LVT names with varNN
            "--old-try-dedup=0", // Inserts dummy exception handlers that break pattern-matching switches
            "--pattern-matching=1", // Resugars `case Type t ->` switches over sealed hierarchies
            "--decompile-switch-expressions=1" // Resugars the SwitchBootstraps.typeSwitch invokedynamic
            );

    @Override
    protected List<String> engineOptions() {
        return DECOMPILER_OPTIONS;
    }
}
