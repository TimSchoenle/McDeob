package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import lombok.Builder;

@Builder
public record ProcessorOptions(
        boolean remap,
        boolean decompile,
        boolean zipDecompileOutput,
        boolean downloadLibraries,
        boolean setupGradleProject,
        DecompilerType decompilerType) {}
