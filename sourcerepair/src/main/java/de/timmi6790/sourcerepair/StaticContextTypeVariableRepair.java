package de.timmi6790.sourcerepair;

import java.util.regex.Pattern;

/**
 * Erases a type argument the decompiler copied into a scope its type variable does not reach.
 *
 * <p>Reconstructing a cast means naming its type, and a decompiler names it from the declaration of
 * whatever the value came from. Where that declaration belongs to an enclosing generic type but the
 * cast sits in a static member, the type variable it names is out of scope.
 *
 * <p>Dropping the type argument leaves the raw type, which is what the cast compiles to either way:
 * a cast to a type variable is erased to its bound, so the raw form is the same instruction and the
 * same guarantee.
 */
final class StaticContextTypeVariableRepair implements SourceRepair {

    private static final Pattern OUT_OF_SCOPE =
            Pattern.compile("^non-static type variable \\w+ cannot be referenced from a static context$");

    @Override
    public String name() {
        return "static-context-type-variable";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        if (!OUT_OF_SCOPE.matcher(diagnostic.summary()).matches()) {
            return false;
        }

        final int reference = document.offsetOf(diagnostic.line(), diagnostic.column());
        if (reference < 0) {
            return false;
        }

        final String text = document.text();
        final int open = openingAngleBefore(text, reference);
        final int close = open < 0 ? -1 : closingAngleAfter(text, open);
        if (close < 0) {
            return false;
        }

        document.replace(open, close + 1, "");
        return true;
    }

    /**
     * Finds the type argument list the reference sits in.
     *
     * @param text source text
     * @param reference offset of the out-of-scope type variable
     * @return offset of the opening angle bracket, or {@code -1} if there is none
     */
    private static int openingAngleBefore(final String text, final int reference) {
        int depth = 0;
        for (int index = reference - 1; index >= 0; index--) {
            final char current = text.charAt(index);
            if (current == '>') {
                depth++;
            } else if (current == '<') {
                if (depth == 0) {
                    return index;
                }
                depth--;
            } else if (current == ';' || current == '{' || current == '}') {
                return -1;
            }
        }
        return -1;
    }

    private static int closingAngleAfter(final String text, final int open) {
        int depth = 0;
        for (int index = open; index < text.length(); index++) {
            final char current = text.charAt(index);
            if (current == '<') {
                depth++;
            } else if (current == '>') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            } else if (current == ';' || current == '{' || current == '}') {
                return -1;
            }
        }
        return -1;
    }
}
