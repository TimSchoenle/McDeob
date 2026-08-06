package com.shanebeestudios.mcdeop.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NativeImageUtil {
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    /**
     * Whether this process is executing as a GraalVM native image.
     *
     * <p>Native images do not provide the {@code jrt} filesystem provider, so any feature that probes the running Java
     * runtime for classes has to be disabled when this returns {@code true}.
     */
    public static boolean isNativeImage() {
        return System.getProperty(IMAGE_CODE_PROPERTY) != null;
    }
}
