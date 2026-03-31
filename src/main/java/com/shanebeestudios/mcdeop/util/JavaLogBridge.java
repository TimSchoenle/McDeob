package com.shanebeestudios.mcdeop.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class JavaLogBridge {
    private static final int MAX_BACKLOG_CHARS = 20_000_000;

    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final StringBuilder BACKLOG = new StringBuilder();

    private static boolean installed;

    private JavaLogBridge() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        System.setOut(createTeeStream(System.out));
        System.setErr(createTeeStream(System.err));
        installed = true;
    }

    public static void registerListener(final Consumer<String> listener) {
        LISTENERS.add(listener);
        synchronized (BACKLOG) {
            if (!BACKLOG.isEmpty()) {
                listener.accept(BACKLOG.toString());
            }
        }
    }

    public static void unregisterListener(final Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    private static PrintStream createTeeStream(final PrintStream original) {
        final OutputStream tee = new TeeOutputStream(original, JavaLogBridge::publish);
        return new PrintStream(tee, true, StandardCharsets.UTF_8);
    }

    private static void publish(final String chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        synchronized (BACKLOG) {
            BACKLOG.append(chunk);
            trimBacklog();
        }
        for (final Consumer<String> listener : LISTENERS) {
            listener.accept(chunk);
        }
    }

    private static void trimBacklog() {
        final int overflow = BACKLOG.length() - MAX_BACKLOG_CHARS;
        if (overflow > 0) {
            BACKLOG.delete(0, overflow);
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream original;
        private final Consumer<String> sink;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private TeeOutputStream(final OutputStream original, final Consumer<String> sink) {
            this.original = original;
            this.sink = sink;
        }

        @Override
        public synchronized void write(final int b) throws IOException {
            this.original.write(b);
            this.buffer.write(b);
            if (b == '\n') {
                this.flushBuffer();
            }
        }

        @Override
        public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
            this.original.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                this.buffer.write(b[i]);
                if (b[i] == '\n') {
                    this.flushBuffer();
                }
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            this.original.flush();
            this.flushBuffer();
        }

        @Override
        public synchronized void close() throws IOException {
            this.flush();
            this.original.close();
        }

        private void flushBuffer() {
            if (this.buffer.size() == 0) {
                return;
            }
            final String chunk = this.buffer.toString(StandardCharsets.UTF_8);
            this.buffer.reset();
            this.sink.accept(chunk);
        }
    }
}
