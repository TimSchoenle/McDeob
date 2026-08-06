package com.shanebeestudios.mcdeop.processor.mache;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The contents of a mache artifact's {@code mache.json}.
 *
 * <p>Every tool version and command line argument of the PaperMC pipeline is pinned here, per Minecraft version.
 * Reproducing Paper's output means using exactly what this file declares rather than McDeob's own defaults.
 *
 * @param minecraftVersion the Minecraft version this mache build targets
 * @param macheVersion the full mache version, including its build number
 * @param codebook the codebook distribution to run; required
 * @param remapper the executable remapper codebook delegates to, or {@code null} when codebook supplies its own
 * @param paramMappings TinyV2 parameter mappings, or {@code null} when the build uses none
 * @param constants the unpick constants jar, or {@code null} when the build does not unpick
 * @param decompiler the decompiler distribution to run; required
 * @param repositories repositories the above artifacts are resolved from
 * @param codebookArguments codebook's command line, with placeholders such as {@code {input}}
 * @param decompilerArguments the decompiler's command line
 */
public record MacheMeta(
        String minecraftVersion,
        String macheVersion,
        MavenArtifact codebook,
        @Nullable MavenArtifact remapper,
        @Nullable MavenArtifact paramMappings,
        @Nullable MavenArtifact constants,
        MavenArtifact decompiler,
        List<MacheRepository> repositories,
        List<String> codebookArguments,
        List<String> decompilerArguments) {

    public MacheMeta {
        repositories = List.copyOf(repositories);
        codebookArguments = List.copyOf(codebookArguments);
        decompilerArguments = List.copyOf(decompilerArguments);
    }
}
