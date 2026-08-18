package com.shanebeestudios.mcdeop.processor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Mustache-style renderer for the generated project files.
 *
 * <p>Supports {@code {{value}}} substitution and {@code {{#section}}...{{/section}}} blocks, which is
 * everything the templates here need.
 */
final class SimpleTemplateEngine {

    private static final Pattern SECTION_PATTERN = Pattern.compile("\\{\\{#([a-zA-Z0-9_-]+)}}");

    String render(final String template, final Map<String, String> values, final Set<String> enabledSections) {
        String rendered = template;

        // Disabled sections are dropped wholesale; enabled ones keep their body and lose the markers.
        for (final String section : findSections(rendered)) {
            if (enabledSections.contains(section)) {
                rendered = rendered.replace("{{#" + section + "}}", "");
                rendered = rendered.replace("{{/" + section + "}}", "");
            } else {
                rendered = sectionBlockPattern(section).matcher(rendered).replaceAll("");
            }
        }

        for (final Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return rendered;
    }

    /**
     * Builds a pattern matching a whole section block, opening and closing markers included.
     *
     * @param section the section name, quoted so it cannot act as a pattern of its own
     * @return the compiled block pattern
     */
    private static Pattern sectionBlockPattern(final String section) {
        final String quoted = Pattern.quote(section);
        return Pattern.compile("(?s)\\{\\{#" + quoted + "}}.*?\\{\\{/" + quoted + "}}");
    }

    private static Set<String> findSections(final String input) {
        final Matcher matcher = SECTION_PATTERN.matcher(input);
        final Set<String> sections = new HashSet<>();
        while (matcher.find()) {
            sections.add(matcher.group(1));
        }
        return sections;
    }
}
