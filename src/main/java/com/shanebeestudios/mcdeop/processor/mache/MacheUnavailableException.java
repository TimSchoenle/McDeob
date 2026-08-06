package com.shanebeestudios.mcdeop.processor.mache;

import java.io.IOException;

/**
 * Thrown when PaperMC has published no mache build for the requested Minecraft version.
 *
 * <p>Distinct from a general failure because it is a normal outcome — mache only covers versions Paper targets —
 * and the user needs to be told to pick a different version or pipeline rather than to retry.
 */
public class MacheUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;

    public MacheUnavailableException(final String message) {
        super(message);
    }
}
