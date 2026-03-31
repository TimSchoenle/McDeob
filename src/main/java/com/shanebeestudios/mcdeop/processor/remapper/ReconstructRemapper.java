package com.shanebeestudios.mcdeop.processor.remapper;

import com.shanebeestudios.mcdeop.processor.Cleanup;
import com.shanebeestudios.mcdeop.processor.ReconConfig;
import io.github.lxgaming.reconstruct.common.Reconstruct;
import io.github.lxgaming.reconstruct.common.manager.TransformerManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReconstructRemapper implements Remapper, Cleanup {
    private static final String RECON_THREADS_PROPERTY = "mcdeob.reconstruct.threads";

    /**
     * {@link Reconstruct} breaks if you run it multiple times because all the transformers are cached with the first config every initialized.
     * This is a nasty hack to clear the static fields in {@link TransformerManager} so that we can run it multiple times.
     */
    private void fixReconstruct() {
        log.debug("Fix Reconstruct");
        try {
            final Class<TransformerManager> affectedClass = TransformerManager.class;
            final Field[] declaredFields = affectedClass.getDeclaredFields();
            for (final Field field : declaredFields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                final Object fieldObject = field.get(null);
                if (fieldObject instanceof Set) {
                    log.debug("Clear Set field in Reconstruct: {}", field.getName());
                    ((Set<?>) fieldObject).clear();
                }
            }
        } catch (final ReflectiveOperationException exception) {
            log.warn("Failed to reset Reconstruct transformer caches", exception);
        }
    }

    @Override
    public void cleanup() {
        this.fixReconstruct();
    }

    @Override
    public void remap(final Path jarPath, final Path mappingsPath, final Path outputDir) {
        final ReconConfig config = new ReconConfig();
        config.setThreads(this.resolveThreads());

        config.setInputPath(jarPath.toAbsolutePath());
        config.setMappingPath(mappingsPath.toAbsolutePath());
        config.setOutputPath(outputDir.toAbsolutePath());

        final Reconstruct reconstruct = new Reconstruct(config);
        reconstruct.load();
    }

    private int resolveThreads() {
        final String configuredValue = System.getProperty(RECON_THREADS_PROPERTY);
        if (configuredValue != null && !configuredValue.isBlank()) {
            try {
                return Math.max(1, Integer.parseInt(configuredValue.trim()));
            } catch (final NumberFormatException exception) {
                log.warn("Invalid {} value '{}', using auto thread selection", RECON_THREADS_PROPERTY, configuredValue);
            }
        }

        // Work around Reconstruct task tracking deadlock in native-image worker execution.
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            final int nativeThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
            log.info("Native runtime detected, using {} Reconstruct thread(s)", nativeThreads);
            return nativeThreads;
        }

        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }
}
