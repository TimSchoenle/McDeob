package com.shanebeestudios.mcdeop.processor.decompiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DecompilerTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"vineflower", "VINEFLOWER", "Vineflower", "  vineflower  "})
    @DisplayName("accepts a CLI value regardless of case or surrounding space")
    void acceptsCliValueLeniently(final String value) {
        assertEquals(DecompilerType.VINEFLOWER, DecompilerType.fromValue(value).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(DecompilerType.class)
    @DisplayName("every constant round-trips through its CLI value")
    void everyConstantRoundTrips(final DecompilerType type) {
        assertEquals(type, DecompilerType.fromValue(type.cliValue()).orElseThrow());
        assertEquals(type, DecompilerType.fromValue(type.name()).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "procyon", "cfr"})
    @DisplayName("rejects an unknown engine")
    void rejectsUnknownEngine(final String value) {
        assertTrue(DecompilerType.fromValue(value).isEmpty());
    }

    @Test
    @DisplayName("rejects null rather than throwing")
    void rejectsNull() {
        assertTrue(DecompilerType.fromValue(null).isEmpty());
    }

    @Test
    @DisplayName("advertises exactly the values it accepts, in declaration order")
    void advertisesEveryAcceptedValue() {
        final List<String> cliValues = DecompilerType.cliValues();

        assertEquals(DecompilerType.values().length, cliValues.size());
        assertEquals(List.of("vineflower", "fernflower", "jadx"), cliValues);
        for (final String value : cliValues) {
            assertTrue(DecompilerType.supportedValues().contains(value));
        }
    }

    @Test
    @DisplayName("the advertised value list cannot be modified by a caller")
    void cliValuesAreImmutable() {
        assertSame(DecompilerType.cliValues(), DecompilerType.cliValues());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> DecompilerType.cliValues().add("procyon"));
    }

    @Test
    @DisplayName("creates the backend matching the constant")
    void createsMatchingBackend() {
        assertInstanceOf(VineflowerDecompiler.class, DecompilerType.VINEFLOWER.createDecompiler());
        assertInstanceOf(FernflowerDecompiler.class, DecompilerType.FERNFLOWER.createDecompiler());
        assertInstanceOf(JadxDecompiler.class, DecompilerType.JADX.createDecompiler());
    }

    @Test
    @DisplayName("only the engines that use them report library support")
    void reportsLibrarySupport() {
        assertTrue(DecompilerType.VINEFLOWER.createDecompiler().supportsExternalLibraries());
        assertTrue(DecompilerType.FERNFLOWER.createDecompiler().supportsExternalLibraries());
        assertTrue(!DecompilerType.JADX.createDecompiler().supportsExternalLibraries());
    }
}
