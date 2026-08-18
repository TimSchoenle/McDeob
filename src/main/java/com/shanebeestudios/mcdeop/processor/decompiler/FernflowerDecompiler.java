package com.shanebeestudios.mcdeop.processor.decompiler;

import java.util.List;

/** The legacy Fernflower-style profile, which is the engine's behaviour with no options applied. */
public class FernflowerDecompiler extends ConsoleEngineDecompiler {
    @Override
    protected List<String> engineOptions() {
        return List.of();
    }
}
