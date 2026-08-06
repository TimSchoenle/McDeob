package de.timmi6790.sourcerepair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Text-level navigation over Java source.
 *
 * <p>The repair rules need to find where an expression ends or which block a position sits in, but
 * not what any of it means. Scanning the text is enough for that and keeps the repair stage free of
 * a parser dependency; the compiler remains the only component that has to understand the code.
 *
 * <p>Every scan skips comments and literals, so a brace inside a string can never be mistaken for
 * structure.
 */
final class JavaText {

    /** Header of an enhanced {@code for}, matched against the text preceding its parenthesis. */
    private static final Pattern FOR_HEADER = Pattern.compile("\\bfor\\s*$");

    private JavaText() {}

    /**
     * Advances past a comment or literal starting at {@code index}.
     *
     * @param text source text
     * @param index offset to inspect
     * @return the offset just past the comment or literal, or {@code index} if none starts there
     */
    static int skipInert(final String text, final int index) {
        final char current = text.charAt(index);
        if (current == '/' && index + 1 < text.length()) {
            final char next = text.charAt(index + 1);
            if (next == '/') {
                final int lineBreak = text.indexOf('\n', index + 2);
                return lineBreak < 0 ? text.length() : lineBreak;
            }
            if (next == '*') {
                final int end = text.indexOf("*/", index + 2);
                return end < 0 ? text.length() : end + 2;
            }
            return index;
        }

        if (current == '"' && text.startsWith("\"\"\"", index)) {
            return endOfDelimited(text, index + 3, "\"\"\"");
        }
        if (current == '"') {
            return endOfDelimited(text, index + 1, "\"");
        }
        if (current == '\'') {
            return endOfDelimited(text, index + 1, "'");
        }
        return index;
    }

    private static int endOfDelimited(final String text, final int start, final String terminator) {
        for (int index = start; index < text.length(); index++) {
            final char current = text.charAt(index);
            if (current == '\\') {
                index++;
                continue;
            }
            if (text.startsWith(terminator, index)) {
                return index + terminator.length();
            }
        }
        return text.length();
    }

    /**
     * Collects the offsets of the braces enclosing a position, outermost first.
     *
     * @param text source text
     * @param offset position to locate
     * @return offsets of the opening braces that are still unclosed at {@code offset}
     */
    static List<Integer> enclosingBraces(final String text, final int offset) {
        final List<Integer> open = new ArrayList<>();
        for (int index = 0; index < offset && index < text.length(); index++) {
            final int skipped = skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }

            final char current = text.charAt(index);
            if (current == '{') {
                open.add(index);
            } else if (current == '}' && !open.isEmpty()) {
                open.remove(open.size() - 1);
            }
        }
        return open;
    }

    /**
     * Checks whether an expression is the sequence of an enhanced {@code for}.
     *
     * <p>An error reported there is about the element type, not about the sequence, so a repair has
     * to act on what the sequence yields rather than on the sequence itself.
     *
     * @param text source text
     * @param start offset of the first character of the expression
     * @return {@code true} if the expression follows the colon of an enhanced {@code for}
     */
    static boolean isEnhancedForSequence(final String text, final int start) {
        int index = start - 1;
        while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
            index--;
        }
        if (index < 0 || text.charAt(index) != ':') {
            return false;
        }

        final List<Integer> parentheses = enclosingParentheses(text, start);
        if (parentheses.isEmpty()) {
            return false;
        }

        final int open = parentheses.get(parentheses.size() - 1);
        return FOR_HEADER.matcher(text).region(Math.max(0, open - 8), open).find();
    }

    /**
     * Collects the offsets of the parentheses enclosing a position, outermost first.
     *
     * @param text source text
     * @param offset position to locate
     * @return offsets of the opening parentheses that are still unclosed at {@code offset}
     */
    static List<Integer> enclosingParentheses(final String text, final int offset) {
        final List<Integer> open = new ArrayList<>();
        for (int index = 0; index < offset && index < text.length(); index++) {
            final int skipped = skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }

            final char current = text.charAt(index);
            if (current == '(') {
                open.add(index);
            } else if (current == ')' && !open.isEmpty()) {
                open.remove(open.size() - 1);
            }
        }
        return open;
    }

    /**
     * Finds the closing brace matching an opening one.
     *
     * @param text source text
     * @param openBrace offset of the opening brace
     * @return offset of the matching closing brace, or {@code -1} if the source is unbalanced
     */
    static int matchingBrace(final String text, final int openBrace) {
        int depth = 0;
        for (int index = openBrace; index < text.length(); index++) {
            final int skipped = skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }

            final char current = text.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * Checks whether an identifier occurs as a whole word in a span.
     *
     * @param text source text
     * @param identifier identifier to look for
     * @param start offset to start searching at
     * @param end offset to stop searching at
     * @return {@code true} if the identifier occurs, not as part of a longer name
     */
    static boolean containsIdentifier(final String text, final String identifier, final int start, final int end) {
        return indexOfIdentifier(text, identifier, start, end) >= 0;
    }

    /**
     * Finds the first whole-word occurrence of an identifier in a span.
     *
     * @param text source text
     * @param identifier identifier to look for
     * @param start offset to start searching at
     * @param end offset to stop searching at
     * @return the offset of the occurrence, or {@code -1} if there is none
     */
    static int indexOfIdentifier(final String text, final String identifier, final int start, final int end) {
        final int limit = Math.min(end, text.length());
        for (int index = Math.max(start, 0); index < limit; index++) {
            final int skipped = skipInert(text, index);
            if (skipped != index) {
                index = skipped - 1;
                continue;
            }
            if (!text.startsWith(identifier, index)) {
                continue;
            }
            if (isIdentifierPart(text, index - 1) || isIdentifierPart(text, index + identifier.length())) {
                continue;
            }
            if (index + identifier.length() > limit) {
                return -1;
            }
            return index;
        }
        return -1;
    }

    private static boolean isIdentifierPart(final String text, final int index) {
        return index >= 0 && index < text.length() && Character.isJavaIdentifierPart(text.charAt(index));
    }
}
