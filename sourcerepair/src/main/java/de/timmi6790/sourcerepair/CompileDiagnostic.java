package de.timmi6790.sourcerepair;

import java.nio.file.Path;

/**
 * A single compiler error reported against a decompiled source file.
 *
 * <p>Positions are one-based, matching what the compiler reports, and refer to the file contents as
 * they were when the diagnostic was produced. A repair therefore has to be applied to the same
 * revision of the file, which the repair loop guarantees by recompiling after every round.
 *
 * @param file source file the error was reported against
 * @param line one-based line of the offending construct
 * @param column one-based column of the offending construct
 * @param startPosition offset of the first character of the offending construct, or {@code -1}
 * @param endPosition offset just past its last character, or {@code -1}
 * @param message the compiler message, including any detail lines
 */
public record CompileDiagnostic(Path file, int line, int column, int startPosition, int endPosition, String message) {

    /**
     * Whether the compiler reported the full extent of the offending construct.
     *
     * <p>The reported line and column point at whichever token reads best in a console message,
     * which for a call chain is the parenthesis of the last call rather than the start of the
     * receiver. Only the span identifies the whole expression, so a rewrite that has to replace one
     * needs this to be true.
     *
     * @return {@code true} if {@link #startPosition} and {@link #endPosition} delimit a span
     */
    public boolean hasSpan() {
        return this.startPosition >= 0 && this.endPosition > this.startPosition;
    }

    /**
     * The first line of {@link #message}, which carries the error kind.
     *
     * <p>Detail lines below it explain an error but never identify it, so rules match on this line
     * alone and read the details only once they have decided the error is theirs.
     *
     * @return the first line of the message
     */
    public String summary() {
        final int lineBreak = this.message.indexOf('\n');
        return lineBreak < 0 ? this.message : this.message.substring(0, lineBreak);
    }
}
