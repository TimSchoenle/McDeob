package de.timmi6790.sourcerepair;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores a cast the decompiler dropped.
 *
 * <p>Generic casts leave no trace in the bytecode: {@code (Brain<Hoglin>) super.getBrain()} and
 * {@code super.getBrain()} compile to the same instructions. A decompiler that does not run full
 * type inference therefore emits the expression without its cast, and the result no longer compiles
 * even though it describes the original program exactly.
 *
 * <p>The compiler names the type the expression has to have, which is precisely the cast that was
 * lost, so the repair is a recovery rather than a guess. Where the resulting cast is itself illegal
 * — converting between two parameterisations of the same type, say — the next round reports the
 * cast expression instead, and it is widened through {@code Object} the way source code would have
 * to do it by hand.
 */
final class MissingCastRepair implements SourceRepair {

    private static final Pattern CONVERSION =
            Pattern.compile("^(.+?) cannot be converted to (.+?)$", Pattern.MULTILINE);

    /**
     * Errors whose reported span is the value that has the wrong type, with the conversion spelled
     * out on a detail line below.
     *
     * <p>Every other error that mentions a conversion — an argument mismatch during type inference,
     * say — reports the span of the enclosing call instead, where a cast would apply to the wrong
     * expression entirely.
     */
    private static final Set<String> DETAILED_SUMMARIES = Set.of(
            "incompatible types: bad type in conditional expression",
            "incompatible types: bad return type in lambda expression");

    /**
     * Type text the compiler prints but source code cannot name: captured wildcards, disambiguated
     * type variables, intersection types, and messages elided for length.
     */
    private static final Pattern INEXPRESSIBLE =
            Pattern.compile("CAP#|capture#|#\\d|\\[\\.\\.\\.]|&|\\.\\.\\.|<any>|<>");

    /** Splits a type into the names it is built from, discarding punctuation and wildcards. */
    private static final Pattern TYPE_NAME_SEPARATOR = Pattern.compile("[<>,\\[\\]?\\s]+|\\bextends\\b|\\bsuper\\b");

    /** A parenthesised type, as opposed to a parenthesised expression. */
    private static final Pattern TYPE_IN_PARENTHESES =
            Pattern.compile("\\s*[\\w.$]+(?:\\s*<.*>)?(?:\\s*\\[\\s*])*\\s*", Pattern.DOTALL);

    /** The widening this repair inserts; recognised so it is never inserted twice. */
    private static final String ERASURE_BRIDGE = "(Object)";

    /** Opening of the cast applied to the sequence of an enhanced {@code for}. */
    private static final String SEQUENCE_CAST = "(Iterable<";

    @Override
    public String name() {
        return "missing-cast";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        final String summary = diagnostic.summary();
        if (!summary.startsWith("incompatible types:")) {
            return false;
        }

        final Matcher conversion =
                CONVERSION.matcher(DETAILED_SUMMARIES.contains(summary) ? diagnostic.message() : summary);
        if (!conversion.find()) {
            return false;
        }

        final String targetType = balanced(conversion.group(2).trim());
        final String sourceType = conversion.group(1).trim();
        if (targetType.equals(sourceType) || !isExpressible(targetType, document.text())) {
            return false;
        }
        if (!diagnostic.hasSpan()) {
            return false;
        }

        final int start = diagnostic.startPosition();
        final int end = Math.min(diagnostic.endPosition(), document.text().length());
        if (end <= start) {
            return false;
        }

        final String expression = document.text().substring(start, end);
        if (isAlreadyCastTo(document.text(), start, targetType)) {
            // An earlier round put this very cast in front of the expression and the compiler still
            // rejects it. Repeating it would stack casts without ever changing the outcome.
            return false;
        }

        if (JavaText.isEnhancedForSequence(document.text(), start)) {
            // The type named here is what the loop variable needs, so the sequence has to be cast to
            // something that yields it rather than to the element type itself.
            if (expression.startsWith(SEQUENCE_CAST)) {
                return false;
            }
            document.replace(start, end, SEQUENCE_CAST + targetType + ">)(" + expression + ")");
            return true;
        }

        final int operand = castOperandOffset(expression);
        if (operand > 0) {
            // A cast is already there and the compiler still rejects it, so the two types are not
            // directly convertible. Erasing to Object in between is how the same conversion is
            // written by hand, and is exactly as safe: both are unchecked.
            if (expression.startsWith(ERASURE_BRIDGE, operand)) {
                return false;
            }
            document.insert(start + operand, ERASURE_BRIDGE);
            return true;
        }

        document.replace(start, end, "(" + targetType + ")(" + expression + ")");
        return true;
    }

    /**
     * Checks whether the expression is already preceded by the cast about to be applied.
     *
     * <p>Guards against stacking a cast that has already failed once, whether it came from a previous
     * round or from the decompiler.
     *
     * @param text source text
     * @param start offset of the expression
     * @param targetType the type the cast would name
     * @return {@code true} if that cast already stands in front of the expression
     */
    private static boolean isAlreadyCastTo(final String text, final int start, final String targetType) {
        int end = start;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        final String cast = "(" + targetType + ")";
        return end >= cast.length() && text.startsWith(cast, end - cast.length());
    }

    /**
     * Drops the trailing parentheses a detail line's own punctuation contributes.
     *
     * <p>A detail line reads {@code (argument mismatch; X cannot be converted to Y)}, so the type at
     * the end of it carries a closing parenthesis that is not part of the type.
     *
     * @param type the type as captured from the message
     * @return the type with unmatched trailing parentheses removed
     */
    private static String balanced(final String type) {
        String result = type;
        while (result.endsWith(")") && count(result, ')') > count(result, '(')) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int count(final String text, final char character) {
        int total = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == character) {
                total++;
            }
        }
        return total;
    }

    /**
     * Locates the operand of a cast expression.
     *
     * @param expression the rejected expression
     * @return the offset of the operand, or {@code -1} if the expression is not a cast
     */
    private static int castOperandOffset(final String expression) {
        if (expression.isEmpty() || expression.charAt(0) != '(') {
            return -1;
        }

        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            final char current = expression.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                final boolean isType =
                        TYPE_IN_PARENTHESES.matcher(expression).region(1, index).matches();
                return isType && index + 1 < expression.length() ? index + 1 : -1;
            }
        }
        return -1;
    }

    /**
     * Checks whether a type the compiler printed can be written in this file.
     *
     * <p>Diagnostics name classes in full, so those always resolve. Type variables are printed bare
     * and only resolve where they are in scope, which requiring them to occur in the file
     * approximates. The check errs towards declining, so an unrepaired diagnostic is reported
     * instead of an unresolvable cast being introduced.
     *
     * @param type the type as printed by the compiler
     * @param source the file the cast would be inserted into
     * @return {@code true} if the type is safe to write into this file
     */
    private static boolean isExpressible(final String type, final String source) {
        if (type.isEmpty() || INEXPRESSIBLE.matcher(type).find()) {
            return false;
        }

        for (final String name : TYPE_NAME_SEPARATOR.split(type)) {
            if (name.isEmpty() || name.indexOf('.') >= 0) {
                continue;
            }
            if (!JavaText.containsIdentifier(source, name, 0, source.length())) {
                return false;
            }
        }
        return true;
    }
}
