package com.shanebeestudios.mcdeop.processor.mache;

import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/**
 * An unpacked mache artifact.
 *
 * @param artifact the resolved mache coordinates
 * @param meta the parsed {@code mache.json}
 * @param patchesDirectory the {@code patches} directory, or {@code null} if the build ships none
 */
public record MacheBundle(
        MavenArtifact artifact, MacheMeta meta, @Nullable Path patchesDirectory) {}
