package com.shanebeestudios.mcdeop.processor.decompiler;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum DecompilerType {
    VINEFLOWER("vineflower", "Vineflower"),
    FERNFLOWER("fernflower", "Fernflower"),
    JADX("jadx", "JADX");

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
        return Arrays.stream(values())
                .filter(type -> type.cliValue.equals(normalized) || type.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values()).map(DecompilerType::cliValue).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
