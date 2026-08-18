package com.shanebeestudios.mcdeop.processor.decompiler;

import com.shanebeestudios.mcdeop.util.NativeImageUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

/**
 * Base for the backends driven by Vineflower's {@link ConsoleDecompiler} entry point.
 *
 * <p>Vineflower and the Fernflower compatibility profile differ only in the options they pass, so the
 * argument assembly and the runtime-scan decision live here rather than being duplicated per engine.
 */
abstract class ConsoleEngineDecompiler implements Decompiler {

    /**
     * Engine options to pass ahead of the input and output paths.
     *
     * @return the options for this engine, in the order they should be passed
     */
    protected abstract List<String> engineOptions();

    /**
     * Letting the decompiler read the JDK's type hierarchy removes redundant casts and restores
     * {@code @Override} annotations, but the runtime scan needs the {@code jrt} filesystem provider
     * that native images do not ship.
     *
     * @return the {@code --include-runtime} option appropriate for this runtime
     */
    private static String includeRuntimeOption() {
        return NativeImageUtil.isNativeImage() ? "--include-runtime=0" : "--include-runtime=1";
    }

    @Override
    public void decompile(final Path jarPath, final Path outputDir, final List<Path> libraries) {
        final List<String> options = this.engineOptions();
        final List<String> args = new ArrayList<>(options.size() + libraries.size() + 3);

        args.addAll(options);
        args.add(includeRuntimeOption());
        for (final Path library : libraries) {
            args.add("--add-external=" + library.toAbsolutePath());
        }
        args.add(jarPath.toAbsolutePath().toString());
        args.add(outputDir.toAbsolutePath().toString());

        ConsoleDecompiler.main(args.toArray(String[]::new));
    }

    @Override
    public boolean supportsExternalLibraries() {
        return true;
    }
}
