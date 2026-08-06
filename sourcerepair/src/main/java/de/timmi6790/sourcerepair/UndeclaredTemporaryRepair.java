package de.timmi6790.sourcerepair;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declares a temporary the decompiler assigned to but never introduced.
 *
 * <p>Desugared record patterns assign their component values inside a synthetic {@code try} block
 * and read them back after it. Recovering that shape leaves the temporary assigned in one block and
 * read in another, with no declaration in either.
 *
 * <p>The type is taken from the statement that reads the temporary back, and the declaration is
 * placed in the innermost block that contains every mention of it, which is the narrowest scope in
 * which the original variable could have lived.
 */
final class UndeclaredTemporaryRepair implements SourceRepair {

    private static final Pattern MISSING_VARIABLE = Pattern.compile("symbol:\\s*variable (\\w+)");

    /** Type names as they appear in a declaration, including generics and array dimensions. */
    private static final String TYPE = "[\\w.$]+(?:\\s*<[^;=]*>)?(?:\\s*\\[\\s*])*";

    /** Headers of blocks that hold members rather than statements. */
    private static final Pattern TYPE_BODY_HEADER =
            Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\b|\\bnew\\b[^()]*\\([^()]*\\)\\s*$");

    /** A switch body may only open with a label, so a declaration cannot go at the top of one. */
    private static final Pattern ARM_LABEL = Pattern.compile("^(?:case|default)\\b");

    @Override
    public String name() {
        return "undeclared-temporary";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        if (!"cannot find symbol".equals(diagnostic.summary())) {
            return false;
        }

        final Matcher missing = MISSING_VARIABLE.matcher(diagnostic.message());
        if (!missing.find()) {
            return false;
        }

        final String name = missing.group(1);
        final int assignment = document.offsetOf(diagnostic.line(), diagnostic.column());
        final String text = document.text();
        if (assignment < 0 || !isAssignmentTarget(text, assignment + name.length())) {
            return false;
        }

        final String type = declaredTypeOf(text, name);
        if (type == null) {
            return false;
        }

        final int block = commonBlock(text, name);
        if (block < 0) {
            return false;
        }

        final int lineStart = document.startOfLine(diagnostic.line());
        final String indent =
                lineStart < 0 ? "" : text.substring(lineStart, assignment).replaceAll("\\S.*", "");
        document.insert(block + 1, "\n" + indent + type + " " + name + ";");
        return true;
    }

    /** @return whether the offset is followed by a single {@code =}, marking an assignment */
    private static boolean isAssignmentTarget(final String text, final int afterName) {
        int index = afterName;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index + 1 < text.length() && text.charAt(index) == '=' && text.charAt(index + 1) != '=';
    }

    /**
     * Recovers the temporary's type from the statement that reads it back.
     *
     * @param text source text
     * @param name the temporary's name
     * @return the declared type, or {@code null} if the temporary is never read into a typed local
     */
    private static String declaredTypeOf(final String text, final String name) {
        final Matcher read = Pattern.compile("(" + TYPE + ")\\s+\\w+\\s*=\\s*" + Pattern.quote(name) + "\\s*;")
                .matcher(text);
        return read.find() ? read.group(1).trim() : null;
    }

    /**
     * Finds the innermost block that can hold a declaration covering every mention of the temporary.
     *
     * <p>The innermost block containing all mentions may be a {@code switch} body, which has to open
     * with a label, so the search widens until it reaches a block that accepts statements. It never
     * widens into a type body: a declaration there would be a field, not a local.
     *
     * @param text source text
     * @param name the temporary's name
     * @return offset of that block's opening brace, or {@code -1} if there is none
     */
    private static int commonBlock(final String text, final String name) {
        List<Integer> common = null;
        int occurrence = JavaText.indexOfIdentifier(text, name, 0, text.length());
        while (occurrence >= 0) {
            final List<Integer> braces = JavaText.enclosingBraces(text, occurrence);
            common = common == null ? braces : longestCommonPrefix(common, braces);
            occurrence = JavaText.indexOfIdentifier(text, name, occurrence + name.length(), text.length());
        }
        if (common == null) {
            return -1;
        }

        for (int index = common.size() - 1; index >= 0; index--) {
            final int brace = common.get(index);
            if (isTypeBody(text, brace)) {
                return -1;
            }
            if (!opensWithArmLabel(text, brace)) {
                return brace;
            }
        }
        return -1;
    }

    private static boolean isTypeBody(final String text, final int brace) {
        int start = brace;
        while (start > 0) {
            final char current = text.charAt(start - 1);
            if (current == ';' || current == '{' || current == '}') {
                break;
            }
            start--;
        }
        return TYPE_BODY_HEADER.matcher(text.substring(start, brace)).find();
    }

    private static boolean opensWithArmLabel(final String text, final int brace) {
        int index = brace + 1;
        while (index < text.length()) {
            if (Character.isWhitespace(text.charAt(index))) {
                index++;
                continue;
            }
            final int skipped = JavaText.skipInert(text, index);
            if (skipped != index) {
                index = skipped;
                continue;
            }
            return ARM_LABEL.matcher(text).region(index, text.length()).lookingAt();
        }
        return false;
    }

    private static List<Integer> longestCommonPrefix(final List<Integer> first, final List<Integer> second) {
        int shared = 0;
        while (shared < first.size()
                && shared < second.size()
                && first.get(shared).equals(second.get(shared))) {
            shared++;
        }
        return first.subList(0, shared);
    }
}
