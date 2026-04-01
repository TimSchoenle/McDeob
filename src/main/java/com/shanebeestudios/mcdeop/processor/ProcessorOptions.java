package com.shanebeestudios.mcdeop.processor;

import lombok.Builder;

@Builder
public record ProcessorOptions(
        boolean remap,
        boolean decompile,
        boolean zipDecompileOutput,
        boolean downloadLibraries,
        boolean setupGradleProject) {}
