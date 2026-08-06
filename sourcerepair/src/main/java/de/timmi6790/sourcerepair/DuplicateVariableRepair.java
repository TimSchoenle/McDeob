package de.timmi6790.sourcerepair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renames a local the decompiler declared twice in one scope.
 *
 * <p>The arms of a {@code switch} statement share a single scope, but each arm was a separate scope
 * in the bytecode's line table. A decompiler that recovers the original variable names per arm
 * therefore emits the same name once per arm, which the compiler rejects even though the arms can
 * never both run.
 *
 * <p>Only the rejected declaration and the uses that belong to it are renamed. Renaming stops at the
 * next {@code case} or {@code default} label, so the arm that legitimately owns the name keeps it.
 */
final class DuplicateVariableRepair implements SourceRepair {

    private static final Pattern ALREADY_DEFINED = Pattern.compile("^variable (\\w+) is already defined\\b");

    /** A label starts a new arm, and with it a new set of variables that must not be renamed. */
    private static final Pattern ARM_LABEL = Pattern.compile("\\b(?:case|default)\\b");

    @Override
    public String name() {
        return "duplicate-variable";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        final Matcher matcher = ALREADY_DEFINED.matcher(diagnostic.summary());
        if (!matcher.find()) {
            return false;
        }

        final String name = matcher.group(1);
        final int declaration = document.offsetOf(diagnostic.line(), diagnostic.column());
        if (declaration < 0 || !document.text().startsWith(name, declaration)) {
            return false;
        }

        final int scopeEnd = endOfArm(document.text(), declaration);
        if (scopeEnd < 0) {
            return false;
        }

        final String replacement = freshName(document.text(), name);
        int occurrence = declaration;
        while (occurrence >= 0) {
            document.replace(occurrence, occurrence + name.length(), replacement);
            occurrence = JavaText.indexOfIdentifier(document.text(), name, occurrence + name.length(), scopeEnd);
        }
        return true;
    }

    /**
     * Finds where the declaration's arm ends.
     *
     * @param text source text
     * @param declaration offset of the redeclared name
     * @return offset of the next arm label or of the end of the enclosing block, or {@code -1} if
     *     the declaration is not inside a block
     */
    private static int endOfArm(final String text, final int declaration) {
        final var braces = JavaText.enclosingBraces(text, declaration);
        if (braces.isEmpty()) {
            return -1;
        }

        final int blockEnd = JavaText.matchingBrace(text, braces.get(braces.size() - 1));
        if (blockEnd < 0) {
            return -1;
        }

        final int nextLabel = nextArmLabel(text, declaration, blockEnd);
        return nextLabel < 0 ? blockEnd : nextLabel;
    }

    private static int nextArmLabel(final String text, final int from, final int limit) {
        int depth = 0;
        for (int index = from; index < limit; index++) {
            final int skipped = JavaText.skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }

            final char current = text.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
            } else if (depth == 0 && (current == 'c' || current == 'd')) {
                final Matcher label = ARM_LABEL.matcher(text).region(index, Math.min(index + 8, limit));
                if (label.lookingAt()) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String freshName(final String text, final String name) {
        for (int suffix = 1; ; suffix++) {
            final String candidate = name + suffix;
            if (!JavaText.containsIdentifier(text, candidate, 0, text.length())) {
                return candidate;
            }
        }
    }
}
