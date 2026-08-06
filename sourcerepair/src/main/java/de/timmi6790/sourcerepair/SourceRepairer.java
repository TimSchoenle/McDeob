package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes a decompiled source tree compile.
 *
 * <p>Decompilers reconstruct the program a class file describes, not the source it was compiled
 * from, and the two differ wherever the compiler erased something. Casts between parameterisations
 * vanish, per-scope variable names collide once scopes are merged, and desugared patterns leave
 * temporaries without declarations. Each of those produces output that is a faithful description of
 * the bytecode yet is not a legal Java program.
 *
 * <p>The compiler is the only component that can tell which of those has happened and where, so it
 * drives the repair: compile, hand each error to the {@link SourceRepair} that recognises it,
 * recompile.
 *
 * <p>Repairs read a single diagnostic, not the program, so one can misjudge its context and leave a
 * file worse than it was. Every file is therefore judged on its own after the next round: one whose
 * error count rose is rolled back and taken out of the run, while the files that improved keep their
 * repairs. A misjudged repair costs the file it was made in and nothing else, and no file can end up
 * worse than it started.
 *
 * <p>Whatever is still broken when the rounds end is reported and left alone. A release that hits a
 * defect no repair covers still produces sources, minus the guarantee that they compile.
 */
@Slf4j
public final class SourceRepairer {

    /**
     * A repair can expose an error that was previously masked, so several rounds are expected. The
     * limit only bounds pathological cases; rounds normally stop on their own well before it.
     */
    private static final int MAX_ROUNDS = 12;

    private static final String JAVA_SUFFIX = ".java";

    /**
     * Ordered so that the repairs recognising a specific defect run before the general ones. Both
     * {@link UndeclaredTemporaryRepair} and {@link CatchParameterRepair} answer to {@code cannot find
     * symbol}, and only the former can tell its case apart with certainty.
     */
    private static List<SourceRepair> repairsFor(final SourceTree sourceTree) {
        return List.of(
                new DuplicateVariableRepair(),
                new UndeclaredTemporaryRepair(),
                new CatchParameterRepair(),
                new StaticContextTypeVariableRepair(),
                new FunctionalInterfaceRepair(sourceTree),
                new MissingCastRepair());
    }

    /**
     * The outcome of a repair run.
     *
     * @param attempted whether the tree was compiled at all; {@code false} when no compiler was
     *     available
     * @param rounds how many compile-and-repair rounds were kept
     * @param repairs how many edits were applied in total
     * @param remaining errors that no repair could resolve
     */
    public record RepairReport(boolean attempted, int rounds, int repairs, List<CompileDiagnostic> remaining) {

        /** @return whether the tree compiles */
        public boolean clean() {
            return this.attempted && this.remaining.isEmpty();
        }
    }

    /**
     * A file edited in the previous round, kept so the edit can be judged and undone.
     *
     * @param document the edited file, holding the contents it had before the edit
     * @param repairs how many edits were applied to it
     * @param errorsBefore how many errors it had before the edit
     */
    private record PendingEdit(SourceDocument document, int repairs, int errorsBefore) {}

    /**
     * Repairs a source tree in place.
     *
     * @param sourceRoot directory holding the sources
     * @param classpath jars the sources compile against
     * @param release the Java release the sources target
     * @return what the run achieved
     * @throws IOException if the sources cannot be read or written
     */
    public RepairReport repair(final Path sourceRoot, final List<Path> classpath, final int release)
            throws IOException {
        final JavaSourceCompiler compiler = JavaSourceCompiler.create(release);
        if (compiler == null) {
            return new RepairReport(false, 0, 0, List.of());
        }

        final List<Path> sources = collectSources(sourceRoot);
        if (sources.isEmpty()) {
            return new RepairReport(false, 0, 0, List.of());
        }

        log.info("Type-checking {} source files against {} libraries", sources.size(), classpath.size());

        final List<SourceRepair> repairs = repairsFor(new SourceTree(sourceRoot));

        final Set<Path> abandoned = new HashSet<>();
        List<PendingEdit> pending = List.of();
        List<CompileDiagnostic> diagnostics = List.of();
        int previousErrors = Integer.MAX_VALUE;
        int applied = 0;
        int round = 0;

        while (round < MAX_ROUNDS) {
            diagnostics = compiler.compile(sources, classpath);
            applied -= rollBackRegressions(pending, countErrorsByFile(diagnostics), abandoned);
            if (diagnostics.isEmpty()) {
                log.info("Sources compile cleanly after {} repair(s)", applied);
                return new RepairReport(true, round, applied, List.of());
            }
            if (diagnostics.size() >= previousErrors) {
                // Every repair of the last round was undone again. Nothing left is within reach of
                // the repairs available, and another round would only repeat the same attempts.
                return new RepairReport(true, round, applied, diagnostics);
            }
            previousErrors = diagnostics.size();

            round++;
            pending = applyRepairs(repairs, diagnostics, abandoned);
            final int roundRepairs =
                    pending.stream().mapToInt(PendingEdit::repairs).sum();
            log.info("Repair round {}: {} error(s), {} repaired", round, diagnostics.size(), roundRepairs);
            if (roundRepairs == 0) {
                return new RepairReport(true, round - 1, applied, diagnostics);
            }
            applied += roundRepairs;
        }

        return new RepairReport(true, round, applied, diagnostics);
    }

    /**
     * Undoes the edits that left their file with more errors than it had.
     *
     * @param pending the files edited in the previous round
     * @param errorsByFile how many errors each file has now
     * @param abandoned files to leave alone from now on; regressing files are added to it
     * @return how many repairs were undone
     * @throws IOException if a source file cannot be written
     */
    private static int rollBackRegressions(
            final List<PendingEdit> pending, final Map<Path, Integer> errorsByFile, final Set<Path> abandoned)
            throws IOException {
        int undone = 0;
        for (final PendingEdit edit : pending) {
            final Path file = edit.document().file();
            if (errorsByFile.getOrDefault(file, 0) <= edit.errorsBefore()) {
                continue;
            }

            log.debug(
                    "Reverting {}: {} error(s) after repair, {} before",
                    file.getFileName(),
                    errorsByFile.get(file),
                    edit.errorsBefore());
            edit.document().restore();
            abandoned.add(file);
            undone += edit.repairs();
        }
        return undone;
    }

    /**
     * Runs every applicable repair over one round's diagnostics.
     *
     * @param repairs the repairs to try, in order
     * @param diagnostics the errors of this round
     * @param abandoned files that must not be edited again
     * @return the edits made, so they can be judged next round
     * @throws IOException if a source file cannot be read or written
     */
    private List<PendingEdit> applyRepairs(
            final List<SourceRepair> repairs, final List<CompileDiagnostic> diagnostics, final Set<Path> abandoned)
            throws IOException {
        final Map<Path, Integer> errorsByFile = countErrorsByFile(diagnostics);
        final Map<Path, SourceDocument> documents = new LinkedHashMap<>();
        for (final CompileDiagnostic diagnostic : diagnostics) {
            if (abandoned.contains(diagnostic.file())) {
                continue;
            }

            SourceDocument document = documents.get(diagnostic.file());
            if (document == null) {
                document = SourceDocument.read(diagnostic.file());
                documents.put(diagnostic.file(), document);
            }
            stage(repairs, diagnostic, document);
        }

        final List<PendingEdit> written = new ArrayList<>();
        for (final SourceDocument document : documents.values()) {
            if (!document.hasPendingEdits()) {
                continue;
            }

            final int applied = document.flush();
            if (applied > 0) {
                document.save();
                written.add(new PendingEdit(document, applied, errorsByFile.get(document.file())));
            }
        }
        return written;
    }

    private static Map<Path, Integer> countErrorsByFile(final List<CompileDiagnostic> diagnostics) {
        final Map<Path, Integer> errorsByFile = new HashMap<>();
        for (final CompileDiagnostic diagnostic : diagnostics) {
            errorsByFile.merge(diagnostic.file(), 1, Integer::sum);
        }
        return errorsByFile;
    }

    private void stage(
            final List<SourceRepair> repairs, final CompileDiagnostic diagnostic, final SourceDocument document) {
        for (final SourceRepair repair : repairs) {
            if (repair.apply(diagnostic, document)) {
                log.debug(
                        "{}: {}:{} {}",
                        repair.name(),
                        document.file().getFileName(),
                        diagnostic.line(),
                        diagnostic.summary());
                return;
            }
        }
    }

    private static List<Path> collectSources(final Path sourceRoot) throws IOException {
        final List<Path> sources = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
                if (file.getFileName().toString().endsWith(JAVA_SUFFIX)) {
                    sources.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sources;
    }
}
