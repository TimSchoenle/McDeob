package de.timmi6790.sourcerepair;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reunites a catch parameter with the name its body uses.
 *
 * <p>When nested handlers carry the same name in the local variable table, the decompiler renames
 * the declarations to keep them distinct but leaves the references pointing at the original name.
 * The handler body then reads a variable that no longer exists.
 *
 * <p>The body is authoritative here: it is what the original code said, so the declaration is moved
 * back onto that name rather than the references being rewritten. The repair only applies when the
 * declared name is genuinely unused in the body, which is what distinguishes this defect from code
 * that merely references something declared elsewhere.
 */
final class CatchParameterRepair implements SourceRepair {

    private static final Pattern MISSING_VARIABLE = Pattern.compile("symbol:\\s*variable (\\w+)");

    /** Matches the parameter of the {@code catch} clause a block belongs to. */
    private static final Pattern CATCH_CLAUSE =
            Pattern.compile("catch\\s*\\(\\s*(?:final\\s+)?[\\w.$]+(?:\\s*\\|\\s*[\\w.$]+)*\\s+(\\w+)\\s*\\)\\s*$");

    @Override
    public String name() {
        return "catch-parameter";
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
        final int usage = document.offsetOf(diagnostic.line(), diagnostic.column());
        if (usage < 0) {
            return false;
        }

        final String text = document.text();
        final List<Integer> braces = JavaText.enclosingBraces(text, usage);
        for (int index = braces.size() - 1; index >= 0; index--) {
            final int blockStart = braces.get(index);
            final Matcher clause = CATCH_CLAUSE.matcher(text).region(lineStartBefore(text, blockStart), blockStart);
            if (!clause.find()) {
                continue;
            }

            final int blockEnd = JavaText.matchingBrace(text, blockStart);
            if (blockEnd < 0 || JavaText.containsIdentifier(text, clause.group(1), blockStart, blockEnd)) {
                continue;
            }

            document.replace(clause.start(1), clause.end(1), name);
            return true;
        }
        return false;
    }

    /** @return the offset of the start of the line containing {@code offset} */
    private static int lineStartBefore(final String text, final int offset) {
        final int lineBreak = text.lastIndexOf('\n', offset);
        return lineBreak < 0 ? 0 : lineBreak + 1;
    }
}
