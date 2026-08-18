package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import java.util.Optional;
import lombok.Builder;

@Builder
public record ProcessorOptions(
        boolean remap,
        boolean decompile,
        boolean zipDecompileOutput,
        boolean downloadLibraries,
        boolean setupGradleProject,
        DecompilerType decompilerType) {

    /**
     * Checks that the selected steps can run together.
     *
     * <p>Owned by the options rather than by each caller: the command line and the processor each
     * enforced these rules separately, with their own wording, and nothing kept the two in step.
     *
     * @return a description of the first conflict found, or empty when the combination is valid
     */
    public Optional<String> validate() {
        if (this.setupGradleProject && !this.decompile) {
            return Optional.of("Gradle project setup requires decompilation to be enabled.");
        }
        if (this.setupGradleProject && !this.downloadLibraries) {
            return Optional.of("Gradle project setup requires library downloads to be enabled.");
        }
        return Optional.empty();
    }

    /**
     * @return the selected decompiler, falling back to the default when none was chosen
     */
    public DecompilerType decompilerTypeOrDefault() {
        return this.decompilerType == null ? DecompilerType.VINEFLOWER : this.decompilerType;
    }
}
