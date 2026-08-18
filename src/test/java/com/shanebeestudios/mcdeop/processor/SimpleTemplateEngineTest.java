package com.shanebeestudios.mcdeop.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimpleTemplateEngineTest {

    private final SimpleTemplateEngine engine = new SimpleTemplateEngine();

    @Test
    @DisplayName("substitutes values")
    void substitutesValues() {
        final String rendered = this.engine.render("name = {{name}}", Map.of("name", "McDeob"), Set.of());

        assertEquals("name = McDeob", rendered);
    }

    @Test
    @DisplayName("substitutes a value used more than once")
    void substitutesRepeatedValues() {
        final String rendered = this.engine.render("{{v}}-{{v}}", Map.of("v", "x"), Set.of());

        assertEquals("x-x", rendered);
    }

    @Test
    @DisplayName("keeps the body of an enabled section and drops its markers")
    void keepsEnabledSection() {
        final String rendered = this.engine.render("a{{#on}}BODY{{/on}}b", Map.of(), Set.of("on"));

        assertEquals("aBODYb", rendered);
    }

    @Test
    @DisplayName("drops a disabled section entirely")
    void dropsDisabledSection() {
        final String rendered = this.engine.render("a{{#off}}BODY{{/off}}b", Map.of(), Set.of());

        assertEquals("ab", rendered);
    }

    @Test
    @DisplayName("drops a multi-line disabled section")
    void dropsMultiLineDisabledSection() {
        final String template = """
                start
                {{#off}}
                dropped
                {{/off}}
                end
                """;

        final String rendered = this.engine.render(template, Map.of(), Set.of());

        assertFalse(rendered.contains("dropped"));
        assertTrue(rendered.contains("start"));
        assertTrue(rendered.contains("end"));
    }

    @Test
    @DisplayName("resolves values inside an enabled section")
    void resolvesValuesInsideEnabledSection() {
        final String rendered = this.engine.render("{{#on}}v={{v}}{{/on}}", Map.of("v", "1"), Set.of("on"));

        assertEquals("v=1", rendered);
    }

    @Test
    @DisplayName("handles independent sections in one template")
    void handlesMultipleSections() {
        final String rendered = this.engine.render("{{#a}}A{{/a}}|{{#b}}B{{/b}}", Map.of(), Set.of("a"));

        assertEquals("A|", rendered);
    }

    @Test
    @DisplayName("leaves an unknown placeholder untouched rather than emptying it")
    void leavesUnknownPlaceholders() {
        final String rendered = this.engine.render("{{missing}}", Map.of(), Set.of());

        assertEquals("{{missing}}", rendered);
    }
}
