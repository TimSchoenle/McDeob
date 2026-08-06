package de.timmi6790.sourcerepair;

import java.util.regex.Pattern;

/**
 * Removes a cast the decompiler added that stops its call from resolving.
 *
 * <p>Where a call's type arguments were inferred, a decompiler has to write down the types erasure
 * left it with, and it does so by casting the arguments. Those casts pin each argument to what the
 * bytecode records, which is the <em>result</em> of the original inference rather than its input.
 * Inference is then left with nothing to work out and the call no longer resolves.
 *
 * <p>Dropping the cast restores what the source said. The compiler is what identifies the call as
 * unresolvable in the first place, so only calls that are already broken are touched, and a removal
 * that does not help is undone along with the rest of its file.
 */
final class ArgumentCastRepair implements SourceRepair {

    /** Errors that mean a call could not be resolved against its arguments. */
    private static final Pattern UNRESOLVED_CALL = Pattern.compile(
            "^(?:no suitable method found for |incompatible types: cannot infer type-variable\\(s\\)).*");

    /** A cast, as opposed to a parenthesised expression: a type and nothing else. */
    private static final Pattern TYPE_IN_PARENTHESES =
            Pattern.compile("\\s*[\\w.$]+(?:\\s*<.*>)?(?:\\s*\\[\\s*])*\\s*", Pattern.DOTALL);

    @Override
    public String name() {
        return "argument-cast";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        if (!UNRESOLVED_CALL.matcher(diagnostic.summary()).matches() || !diagnostic.hasSpan()) {
            return false;
        }

        // The compiler reports the operand of the cast rather than the cast itself, so the cast is
        // what stands immediately in front of the reported expression.
        final String text = document.text();
        final int castEnd = closingParenthesisBefore(text, diagnostic.startPosition());
        if (castEnd < 0) {
            return false;
        }

        final int castStart = matchingOpenParenthesis(text, castEnd);
        if (castStart < 0
                || !TYPE_IN_PARENTHESES
                        .matcher(text)
                        .region(castStart + 1, castEnd)
                        .matches()) {
            return false;
        }

        document.replace(castStart, castEnd + 1, "");
        return true;
    }

    /**
     * Finds the parenthesis that closes immediately before a position.
     *
     * @param text source text
     * @param start offset of the reported expression
     * @return offset of that parenthesis, or {@code -1} if something else precedes the expression
     */
    private static int closingParenthesisBefore(final String text, final int start) {
        int index = start - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        return index >= 0 && text.charAt(index) == ')' ? index : -1;
    }

    /**
     * Finds the parenthesis matching a closing one.
     *
     * @param text source text
     * @param closing offset of the closing parenthesis
     * @return offset of the matching opening parenthesis, or {@code -1} if there is none
     */
    private static int matchingOpenParenthesis(final String text, final int closing) {
        int depth = 0;
        for (int index = closing; index >= 0; index--) {
            final char current = text.charAt(index);
            if (current == ')') {
                depth++;
            } else if (current == '(') {
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
