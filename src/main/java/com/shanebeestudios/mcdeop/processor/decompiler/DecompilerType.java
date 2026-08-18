package com.shanebeestudios.mcdeop.processor.decompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DecompilerType {
    VINEFLOWER("vineflower", "Vineflower"),
    FERNFLOWER("fernflower", "Fernflower"),
    JADX("jadx", "JADX");

    /**
     * The values {@link #fromValue(String)} accepts.
     *
     * <p>Derived from the constants rather than written out, so the command line help cannot come to
     * advertise an engine that does not exist.
     */
    private static final List<String> CLI_VALUES = buildCliValues();

    private static final String SUPPORTED_VALUES = String.join(", ", CLI_VALUES);

    private final String cliValue;
    private final String displayName;

    DecompilerType(final String cliValue, final String displayName) {
        this.cliValue = cliValue;
        this.displayName = displayName;
    }

    public String cliValue() {
        return this.cliValue;
    }

    public Decompiler createDecompiler() {
        return switch (this) {
            case VINEFLOWER -> new VineflowerDecompiler();
            case FERNFLOWER -> new FernflowerDecompiler();
            case JADX -> new JadxDecompiler();
        };
    }

    public static Optional<DecompilerType> fromValue(final String value) {
        if (value == null) {
            return Optional.empty();
        }

        final String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        for (final DecompilerType type : values()) {
            if (type.cliValue.equals(normalized)
                    || type.name().toLowerCase(Locale.ENGLISH).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return SUPPORTED_VALUES;
    }

    /**
     * @return every supported CLI value, in declaration order
     */
    public static List<String> cliValues() {
        return CLI_VALUES;
    }

    private static List<String> buildCliValues() {
        final List<String> values = new ArrayList<>(values().length);
        for (final DecompilerType type : values()) {
            values.add(type.cliValue);
        }
        return List.copyOf(values);
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
