package de.timmi6790.sourcerepair;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives a raw constructor call back its diamond.
 *
 * <p>A decompiler writes {@code new AnimalPanic(2.5F)} for what the source wrote as {@code new
 * AnimalPanic<>(2.5F)}, because erasure leaves no record of the diamond. The raw type that results
 * is not merely untidy: a raw argument erases the whole call it appears in, so the enclosing call
 * stops resolving too.
 *
 * <p>The diamond is only added where the type really does declare type parameters, which is read
 * from its own source. Only calls the compiler has already rejected are touched.
 */
final class RawConstructorRepair implements SourceRepair {

    /** Errors that mean a call could not be resolved against its arguments. */
    private static final Pattern UNRESOLVED_CALL = Pattern.compile(
            "^(?:no suitable method found for |incompatible types: cannot infer type-variable\\(s\\)).*");

    /** A constructor call with neither type arguments nor a diamond. */
    private static final Pattern RAW_CONSTRUCTOR = Pattern.compile("\\bnew\\s+([\\w.$]+)\\s*\\(");

    private final SourceTree sourceTree;

    RawConstructorRepair(final SourceTree sourceTree) {
        this.sourceTree = sourceTree;
    }

    @Override
    public String name() {
        return "raw-constructor";
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

        final Matcher constructor = RAW_CONSTRUCTOR.matcher(text).region(start, end);
        boolean added = false;
        while (constructor.find()) {
            if (this.isGeneric(constructor.group(1), document)) {
                document.insert(constructor.end() - 1, "<>");
                added = true;
            }
        }
        return added;
    }

    /**
     * Checks whether a type declares type parameters, and so can take a diamond.
     *
     * @param typeName the type as written at the constructor call
     * @param document the file the call appears in
     * @return {@code true} if the type is generic
     */
    private boolean isGeneric(final String typeName, final SourceDocument document) {
        final String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
        final Pattern declaration =
                Pattern.compile("\\b(?:class|interface|record)\\s+" + Pattern.quote(simpleName) + "\\s*<");

        // The type is most often declared in the file that uses it or in one named after it; both
        // are cheap to check and a miss only means the repair declines.
        if (declaration.matcher(document.text()).find()) {
            return true;
        }

        final Optional<String> source = this.sourceTree.read(qualifiedNameIn(document.text(), simpleName));
        return source.isPresent() && declaration.matcher(source.get()).find();
    }

    /**
     * Works out where a simple name comes from, using the file's imports.
     *
     * @param text the file referring to the type
     * @param simpleName the type's simple name
     * @return the qualified name, or the simple name if no import covers it
     */
    private static String qualifiedNameIn(final String text, final String simpleName) {
        final Matcher singleImport = Pattern.compile(
                        "^import\\s+([\\w.$]*\\." + Pattern.quote(simpleName) + ");", Pattern.MULTILINE)
                .matcher(text);
        if (singleImport.find()) {
            return singleImport.group(1);
        }

        final Matcher declaredPackage =
                Pattern.compile("^package\\s+([\\w.$]+);", Pattern.MULTILINE).matcher(text);
        return declaredPackage.find() ? declaredPackage.group(1) + "." + simpleName : simpleName;
    }
}
