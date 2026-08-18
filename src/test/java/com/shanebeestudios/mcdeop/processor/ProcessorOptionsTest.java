package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessorOptionsTest {

    @Test
    @DisplayName("accepts a gradle project with decompilation and libraries enabled")
    void acceptsCompleteGradleSetup() {
        final ProcessorOptions options = ProcessorOptions.builder()
                .decompile(true)
                .downloadLibraries(true)
                .setupGradleProject(true)
                .build();

        assertTrue(options.validate().isEmpty());
    }

    @Test
    @DisplayName("rejects a gradle project without decompilation")
    void rejectsGradleProjectWithoutDecompile() {
        final ProcessorOptions options = ProcessorOptions.builder()
                .decompile(false)
                .downloadLibraries(true)
                .setupGradleProject(true)
                .build();

        final Optional<String> conflict = options.validate();
        assertTrue(conflict.isPresent());
        assertTrue(conflict.get().contains("decompil"), "message should name the missing step: " + conflict.get());
    }

    @Test
    @DisplayName("rejects a gradle project without libraries")
    void rejectsGradleProjectWithoutLibraries() {
        final ProcessorOptions options = ProcessorOptions.builder()
                .decompile(true)
                .downloadLibraries(false)
                .setupGradleProject(true)
                .build();

        final Optional<String> conflict = options.validate();
        assertTrue(conflict.isPresent());
        assertTrue(conflict.get().contains("librar"), "message should name the missing step: " + conflict.get());
    }

    @Test
    @DisplayName("places no requirements on a run without a gradle project")
    void acceptsAnythingWithoutGradleProject() {
        final ProcessorOptions options = ProcessorOptions.builder()
                .decompile(false)
                .downloadLibraries(false)
                .setupGradleProject(false)
                .build();

        assertTrue(options.validate().isEmpty());
    }

    @Test
    @DisplayName("falls back to Vineflower when no decompiler was chosen")
    void defaultsToVineflower() {
        assertEquals(
                DecompilerType.VINEFLOWER, ProcessorOptions.builder().build().decompilerTypeOrDefault());
        assertEquals(
                DecompilerType.JADX,
                ProcessorOptions.builder()
                        .decompilerType(DecompilerType.JADX)
                        .build()
                        .decompilerTypeOrDefault());
    }
}
