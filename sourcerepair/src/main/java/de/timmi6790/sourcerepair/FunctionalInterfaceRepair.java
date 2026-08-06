package de.timmi6790.sourcerepair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores the cast that gave a lambda a functional target type.
 *
 * <p>A lambda assigned to an interface that has more than one abstract method was written with a
 * cast to a single-method subtype of it. The cast is erased at compile time — the call site records
 * the subtype in its bootstrap arguments, not in the bytecode of the assignment — so a decompiler
 * that reproduces the field's declared type alone drops it, and the lambda is left with no target.
 *
 * <p>The subtype is recovered from the interface's own source. If it declares more than one
 * single-method subtype the repair declines: the choice would decide which behaviour the lambda gets
 * and the diagnostic says nothing about which was meant, so guessing could produce code that
 * compiles and is wrong.
 */
final class FunctionalInterfaceRepair implements SourceRepair {

    private static final Pattern NOT_FUNCTIONAL =
            Pattern.compile("^incompatible types: (\\S+) is not a functional interface$");

    private static final String FUNCTIONAL_ANNOTATION = "@FunctionalInterface";

    private final SourceTree sourceTree;

    FunctionalInterfaceRepair(final SourceTree sourceTree) {
        this.sourceTree = sourceTree;
    }

    @Override
    public String name() {
        return "functional-interface";
    }

    @Override
    public boolean apply(final CompileDiagnostic diagnostic, final SourceDocument document) {
        final Matcher matcher = NOT_FUNCTIONAL.matcher(diagnostic.summary());
        if (!matcher.matches() || !diagnostic.hasSpan()) {
            return false;
        }

        final String interfaceName = matcher.group(1);
        final Optional<String> declaration = this.sourceTree.read(interfaceName);
        if (declaration.isEmpty()) {
            return false;
        }

        final List<String> candidates = functionalSubtypesOf(declaration.get(), simpleNameOf(interfaceName));
        if (candidates.size() != 1) {
            return false;
        }

        final int start = diagnostic.startPosition();
        final int end = Math.min(diagnostic.endPosition(), document.text().length());
        if (end <= start) {
            return false;
        }

        final String target = interfaceName + "." + candidates.get(0);
        final String expression = document.text().substring(start, end);
        if (expression.startsWith("(" + target + ")")) {
            return false;
        }

        document.replace(start, end, "(" + target + ")(" + expression + ")");
        return true;
    }

    /**
     * Finds the single-method interfaces an interface declares for use as lambda targets.
     *
     * @param declaration source of the interface
     * @param simpleName its simple name, which its own subtypes extend
     * @return names of the nested interfaces that extend it and are marked functional
     */
    private static List<String> functionalSubtypesOf(final String declaration, final String simpleName) {
        final Pattern subtype = Pattern.compile(Pattern.quote(FUNCTIONAL_ANNOTATION)
                + "\\s+(?:\\w+\\s+)*interface\\s+(\\w+)[^{]*\\bextends\\b[^{]*\\b" + Pattern.quote(simpleName) + "\\b");

        final List<String> candidates = new ArrayList<>();
        final Matcher matcher = subtype.matcher(declaration);
        while (matcher.find()) {
            candidates.add(matcher.group(1));
        }
        return candidates;
    }

    private static String simpleNameOf(final String qualifiedName) {
        final int lastSegment = qualifiedName.lastIndexOf('.');
        return lastSegment < 0 ? qualifiedName : qualifiedName.substring(lastSegment + 1);
    }
}
