package de.timmi6790.sourcerepair;

/**
 * Repairs one class of defect a decompiler leaves behind in its output.
 *
 * <p>A repair is driven by a compiler diagnostic rather than by pattern matching on the source, so
 * it only ever touches code the compiler has already rejected. Correct code is therefore never at
 * risk, and a repair that guesses wrong is caught by the next round of compilation.
 *
 * <p>Implementations must be conservative: when anything about the surrounding code is not exactly
 * as expected they decline, leaving the diagnostic to be reported as unrepaired rather than
 * rewriting code they do not understand.
 */
interface SourceRepair {

    /** @return a short name identifying this repair in the log */
    String name();

    /**
     * Attempts to repair one diagnostic.
     *
     * @param diagnostic the error to repair
     * @param document the file the error was reported against, to stage edits on
     * @return {@code true} if this repair handled the diagnostic and staged an edit
     */
    boolean apply(CompileDiagnostic diagnostic, SourceDocument document);
}
