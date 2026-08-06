package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import java.nio.file.Path;
import lombok.Builder;
import org.jetbrains.annotations.Nullable;

/**
 * @param remap whether to remap; ignored by {@link PipelineType#MACHE}, which always remaps
 * @param decompile whether to decompile; ignored by {@link PipelineType#MACHE}, which always decompiles
 * @param zipDecompileOutput whether to also pack the sources into a zip
 * @param downloadLibraries whether to download the release's libraries; ignored by {@link PipelineType#MACHE},
 *     which takes them from the server bundler instead
 * @param setupGradleProject whether to generate a Gradle project around the sources
 * @param decompilerType the decompiler engine; ignored by {@link PipelineType#MACHE}, which uses the release
 *     mache pins
 * @param pipelineType which pipeline to run
 * @param javaHome a Java home used to run mache's toolchain, or {@code null} to detect or download one
 */
@Builder
public record ProcessorOptions(
        boolean remap,
        boolean decompile,
        boolean zipDecompileOutput,
        boolean downloadLibraries,
        boolean setupGradleProject,
        DecompilerType decompilerType,
        PipelineType pipelineType,
        @Nullable Path javaHome) {

    public ProcessorOptions {
        if (pipelineType == null) {
            pipelineType = PipelineType.MOJANG;
        }
    }

    public boolean isMache() {
        return this.pipelineType == PipelineType.MACHE;
    }
}
