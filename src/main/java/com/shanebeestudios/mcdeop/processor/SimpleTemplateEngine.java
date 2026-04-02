package com.shanebeestudios.mcdeop.processor;

import java.util.Map;
import java.util.Set;

final class SimpleTemplateEngine {
    String render(final String template, final Map<String, String> values, final Set<String> enabledSections) {
        String rendered = template;
        for (final String section : enabledSections) {
            rendered = rendered.replace("{{#" + section + "}}", "");
            rendered = rendered.replace("{{/" + section + "}}", "");
        }

        for (final String section : this.findSections(rendered)) {
            if (!enabledSections.contains(section)) {
                rendered = rendered.replaceAll("(?s)\\{\\{#" + section + "}}.*?\\{\\{/" + section + "}}", "");
            }
        }

        for (final Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return rendered;
    }

    private Set<String> findSections(final String input) {
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{#([a-zA-Z0-9_-]+)}}");
        final java.util.regex.Matcher matcher = pattern.matcher(input);
        final java.util.Set<String> sections = new java.util.HashSet<>();
        while (matcher.find()) {
            sections.add(matcher.group(1));
        }
        return sections;
    }
}
