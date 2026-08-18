package com.shanebeestudios.mcdeop.processor.decompiler;

import jadx.api.JadxArgs;
import java.nio.file.Path;
import java.util.List;

public class JadxDecompiler implements Decompiler {
    @Override
    public void decompile(final Path jarPath, final Path outputDir, final List<Path> libraries) {
        final JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setInputFile(jarPath.toFile());
        jadxArgs.setOutDir(outputDir.toFile());
        jadxArgs.setDeobfuscationOn(true);
        jadxArgs.setSkipResources(true);
        jadxArgs.setThreadsCount(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        try (jadx.api.JadxDecompiler jadxDecompiler = new jadx.api.JadxDecompiler(jadxArgs)) {
            jadxDecompiler.load();
            jadxDecompiler.save();
        } catch (final Exception exception) {
            throw new IllegalStateException("JADX failed to decompile " + jarPath.getFileName(), exception);
        }
    }
}
