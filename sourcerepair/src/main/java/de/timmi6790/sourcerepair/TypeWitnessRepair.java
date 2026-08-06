package de.timmi6790.sourcerepair;

import java.util.regex.Pattern;

/**
 * Removes an explicit type argument the decompiler put on a call.
 *
 * <p>Like a cast on an argument, a type witness records what erasure left rather than what the
 * source said. {@code List.<T>of()} pins the element type to the enclosing declaration's variable,
 * where {@code List.of()} would let it be inferred from the call's target, and the pinned version
 * frequently does not fit.
 *
 * <p>Only calls the compiler has already rejected are touched, and a removal that does not help is
 * undone along with the rest of its file.
 */
final class TypeWitnessRepair implements SourceRepair {

    /** Errors that mean a call's type arguments do not work out. */
    private static final Pattern UNRESOLVED_CALL = Pattern.compile("^(?:no suitable method found for "
            + "|incompatible types: cannot infer type-variable\\(s\\)"
            + "|incompatible types: inferred type does not conform to upper bound\\(s\\)"
            + "|incompatible types: invalid method reference"
            + "|method .* cannot be applied to given types;).*");

    /** A type witness: the angle brackets between a receiver's dot and the method name. */
    private static final Pattern WITNESS = Pattern.compile("\\.\\s*<[^<>()]*(?:<[^<>()]*>)?[^<>()]*>\\s*(?=\\w)");

    @Override
    public String name() {
        return "type-witness";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        if (!UNRESOLVED_CALL.matcher(diagnostic.summary()).matches() || !diagnostic.hasSpan()) {
            return false;
        }

        final String text = document.text();
        final int start = Math.max(diagnostic.startPosition(), 0);
        final int end = Math.min(diagnostic.endPosition(), text.length());
        if (end <= start) {
            return false;
        }

        // Only the witness of the reported call is of interest, so the search stops at the first
        // opening parenthesis; anything past it belongs to an argument rather than to this call.
        final int limit = argumentsBegin(text, start, end);
        final java.util.regex.Matcher witness = WITNESS.matcher(text).region(start, limit);
        if (!witness.find()) {
            return false;
        }

        document.replace(witness.start(), witness.end(), ".");
        return true;
    }

    private static int argumentsBegin(final String text, final int start, final int end) {
        for (int index = start; index < end; index++) {
            final int skipped = JavaText.skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }
            if (text.charAt(index) == '(') {
                return index;
            }
        }
        return end;
    }
}
