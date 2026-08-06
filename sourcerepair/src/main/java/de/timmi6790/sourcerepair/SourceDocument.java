package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A source file being repaired, together with the edits staged against it.
 *
 * <p>Edits are collected rather than applied immediately because every diagnostic of a round refers
 * to the same, unmodified revision of the file. Applying them back to front on {@link #flush()}
 * keeps the offsets of the remaining edits valid, and overlapping edits are dropped instead of
 * merged: two rules disagreeing about the same span is a sign that at least one of them misread the
 * code, and the next round re-reports whatever is still broken.
 */
public final class SourceDocument {

    private final Path file;
    private final String originalText;
    private final int[] lineStarts;
    private final List<Edit> edits = new ArrayList<>();

    private String text;

    private record Edit(int start, int end, String replacement) {}

    SourceDocument(final Path file, final String text) {
        this.file = file;
        this.originalText = text;
        this.text = text;
        this.lineStarts = indexLines(text);
    }

    static SourceDocument read(final Path file) throws IOException {
        return new SourceDocument(file, Files.readString(file, StandardCharsets.UTF_8));
    }

    private static int[] indexLines(final String text) {
        final List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                starts.add(index + 1);
            }
        }

        final int[] result = new int[starts.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = starts.get(index);
        }
        return result;
    }

    /**
     * The file contents as they were when the current round of diagnostics was produced.
     *
     * @return the unmodified source text
     */
    public String text() {
        return this.originalText;
    }

    public Path file() {
        return this.file;
    }

    /**
     * Translates a one-based compiler position into an offset into {@link #text()}.
     *
     * @param line one-based line number
     * @param column one-based column number
     * @return the offset, or {@code -1} if the position lies outside the file
     */
    public int offsetOf(final int line, final int column) {
        if (line < 1 || line > this.lineStarts.length || column < 1) {
            return -1;
        }

        final int lineStart = this.lineStarts[line - 1];
        final int lineEnd = line < this.lineStarts.length ? this.lineStarts[line] : this.originalText.length();
        final int offset = lineStart + column - 1;
        return offset > lineEnd ? -1 : offset;
    }

    /** @return the offset just past the end of the given one-based line, excluding its line break */
    public int endOfLine(final int line) {
        if (line < 1 || line > this.lineStarts.length) {
            return -1;
        }

        int offset = line < this.lineStarts.length ? this.lineStarts[line] : this.originalText.length();
        while (offset > 0
                && (this.originalText.charAt(offset - 1) == '\n' || this.originalText.charAt(offset - 1) == '\r')) {
            offset--;
        }
        return offset;
    }

    /** @return the offset of the first character of the given one-based line */
    public int startOfLine(final int line) {
        return line < 1 || line > this.lineStarts.length ? -1 : this.lineStarts[line - 1];
    }

    /**
     * Stages a replacement of the given span.
     *
     * @param start offset of the first character to replace
     * @param end offset just past the last character to replace
     * @param replacement text to put in its place
     */
    public void replace(final int start, final int end, final String replacement) {
        this.edits.add(new Edit(start, end, replacement));
    }

    /**
     * Stages an insertion at the given offset.
     *
     * @param offset offset to insert at
     * @param insertion text to insert
     */
    public void insert(final int offset, final String insertion) {
        this.edits.add(new Edit(offset, offset, insertion));
    }

    /** @return whether any edit has been staged */
    public boolean hasPendingEdits() {
        return !this.edits.isEmpty();
    }

    /**
     * Applies the staged edits and clears them.
     *
     * @return the number of edits applied; overlapping edits are discarded and not counted
     */
    public int flush() {
        this.edits.sort(
                Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end).reversed());

        final StringBuilder builder = new StringBuilder(this.text);
        int applied = 0;
        int lowestTouchedOffset = Integer.MAX_VALUE;
        for (final Edit edit : this.edits) {
            if (edit.start() < 0 || edit.end() > this.text.length() || edit.start() > edit.end()) {
                continue;
            }
            if (edit.end() > lowestTouchedOffset) {
                continue;
            }

            builder.replace(edit.start(), edit.end(), edit.replacement());
            lowestTouchedOffset = edit.start();
            applied++;
        }

        this.edits.clear();
        this.text = builder.toString();
        return applied;
    }

    /**
     * Writes the document back to disk if {@link #flush()} changed it.
     *
     * @throws IOException if the file cannot be written
     */
    void save() throws IOException {
        if (!this.text.equals(this.originalText)) {
            Files.writeString(this.file, this.text, StandardCharsets.UTF_8);
        }
    }

    /**
     * Restores the file to the contents it had before this round's edits.
     *
     * @throws IOException if the file cannot be written
     */
    void restore() throws IOException {
        this.text = this.originalText;
        Files.writeString(this.file, this.originalText, StandardCharsets.UTF_8);
    }
}
