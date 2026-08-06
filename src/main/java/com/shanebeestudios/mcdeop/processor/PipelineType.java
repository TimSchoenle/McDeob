package com.shanebeestudios.mcdeop.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The deobfuscation pipeline to run. */
public enum PipelineType {
    /** McDeob's own pipeline: remap with Reconstruct, then decompile with the selected engine. */
    MOJANG("mojang", "Mojang mappings"),

    /**
     * PaperMC's pipeline: remap with codebook, unpick constants, then decompile and patch exactly as mache
     * specifies for the selected Minecraft version.
     *
     * <p>Produces named parameters and local variables, named constants in place of magic numbers, and source
     * that compiles. Server versions only, and only versions PaperMC has published a mache build for.
     */
    MACHE("mache", "PaperMC mache");

    private final String cliValue;
    private final String displayName;

    PipelineType(final String cliValue, final String displayName) {
        this.cliValue = cliValue;
        this.displayName = displayName;
    }

    public String cliValue() {
        return this.cliValue;
    }

    public static Optional<PipelineType> fromValue(final String value) {
        if (value == null) {
            return Optional.empty();
        }

        final String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        for (final PipelineType type : values()) {
            if (type.cliValue.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return String.join(", ", cliValues());
    }

    /**
     * The values {@link #fromValue(String)} accepts.
     *
     * <p>Derived from the constants rather than written out, so the command line help cannot come to advertise a
     * pipeline that does not exist.
     *
     * @return every supported CLI value, in declaration order
     */
    public static List<String> cliValues() {
        final List<String> values = new ArrayList<>(values().length);
        for (final PipelineType type : values()) {
            values.add(type.cliValue);
        }
        return values;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
