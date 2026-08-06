package de.timmi6790.sourcerepair;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Type-checks a source tree using the compiler of the runtime McDeob is running on.
 *
 * <p>Generated class files are discarded: the repair loop only ever needs the errors, and writing
 * several thousand class files per round would dominate its runtime.
 */
@Slf4j
final class InProcessJavaCompiler implements JavaSourceCompiler {

    private final JavaCompiler compiler;
    private final int release;

    private InProcessJavaCompiler(final JavaCompiler compiler, final int release) {
        this.compiler = compiler;
        this.release = release;
    }

    /**
     * Creates a compiler backed by the running runtime.
     *
     * @param release the Java release the sources target
     * @return the compiler, or {@code null} if this runtime cannot compile that release
     */
    static InProcessJavaCompiler create(final int release) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.debug("This runtime ships no Java compiler; an external one is needed instead");
            return null;
        }

        final int runtimeFeature = Runtime.version().feature();
        if (runtimeFeature < release) {
            log.debug("Sources target Java {} but this runtime is Java {}", release, runtimeFeature);
            return null;
        }

        return new InProcessJavaCompiler(compiler, release);
    }

    @Override
    public List<CompileDiagnostic> compile(final List<Path> sources, final List<Path> classpath) throws IOException {
        final DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();

        try (StandardJavaFileManager standardFileManager =
                        this.compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8);
                JavaFileManager fileManager = new DiscardingFileManager(standardFileManager)) {

            final List<String> options = JavaSourceCompiler.optionsFor(this.release, classpath);

            final Iterable<? extends JavaFileObject> units = standardFileManager.getJavaFileObjectsFromPaths(sources);
            this.compiler
                    .getTask(Writer.nullWriter(), fileManager, collector, options, null, units)
                    .call();
        }

        return toDiagnostics(collector);
    }

    private static List<CompileDiagnostic> toDiagnostics(final DiagnosticCollector<JavaFileObject> collector) {
        final List<CompileDiagnostic> diagnostics = new ArrayList<>();
        for (final javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
            if (diagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR) {
                continue;
            }

            final JavaFileObject source = diagnostic.getSource();
            if (source == null) {
                continue;
            }

            diagnostics.add(new CompileDiagnostic(
                    Path.of(source.toUri()),
                    (int) diagnostic.getLineNumber(),
                    (int) diagnostic.getColumnNumber(),
                    (int) diagnostic.getStartPosition(),
                    (int) diagnostic.getEndPosition(),
                    diagnostic.getMessage(Locale.ROOT)));
        }
        return diagnostics;
    }

    /** Sends generated class files nowhere; only diagnostics are of interest. */
    private static final class DiscardingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private DiscardingFileManager(final StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                final Location location,
                final String className,
                final JavaFileObject.Kind kind,
                final FileObject sibling) {
            return new DiscardingFileObject(className, kind);
        }
    }

    private static final class DiscardingFileObject extends SimpleJavaFileObject {

        private DiscardingFileObject(final String className, final Kind kind) {
            super(URI.create("discard:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return OutputStream.nullOutputStream();
        }
    }
}
