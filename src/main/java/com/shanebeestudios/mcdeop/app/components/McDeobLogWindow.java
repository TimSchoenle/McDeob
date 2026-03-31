package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.util.JavaLogBridge;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class McDeobLogWindow extends VBox {
    private static final int MAX_VISIBLE_CHARS = 150_000;
    private static final int MAX_APPEND_PER_FLUSH = 12_000;

    private final TextArea logArea;
    private final Consumer<String> listener;
    private final StringBuilder pendingText = new StringBuilder();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    public McDeobLogWindow() {
        super(6);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(4, 0, 0, 0));
        this.setFillWidth(true);

        final Label title = new Label("Activity Log");
        title.getStyleClass().add("logs-label");

        final Button copyButton = new Button("Copy");
        copyButton.getStyleClass().add("ghost-button");
        copyButton.setOnAction(e -> this.copyLogs());

        final Button clearButton = new Button("Clear");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> this.clearLogs());

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final HBox toolbar = new HBox(8, title, spacer, copyButton, clearButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("log-toolbar");

        this.logArea = new TextArea();
        this.logArea.setEditable(false);
        this.logArea.setWrapText(false);
        this.logArea.setPrefRowCount(14);
        this.logArea.setFocusTraversable(false);
        this.logArea.getStyleClass().add("logs-window");

        VBox.setVgrow(this.logArea, Priority.ALWAYS);
        this.getChildren().addAll(toolbar, this.logArea);

        this.listener = this::appendLog;
        JavaLogBridge.registerListener(this.listener);
    }

    public void dispose() {
        JavaLogBridge.unregisterListener(this.listener);
    }

    private void appendLog(final String chunk) {
        synchronized (this.pendingText) {
            this.pendingText.append(chunk);
        }
        this.scheduleFlush();
    }

    private void scheduleFlush() {
        if (this.flushScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::flushPendingOnFxThread);
        }
    }

    private void flushPendingOnFxThread() {
        int appended = 0;
        while (appended < MAX_APPEND_PER_FLUSH) {
            final String chunk;
            synchronized (this.pendingText) {
                if (this.pendingText.isEmpty()) {
                    break;
                }
                final int take = Math.min(MAX_APPEND_PER_FLUSH - appended, this.pendingText.length());
                chunk = this.pendingText.substring(0, take);
                this.pendingText.delete(0, take);
            }
            this.appendOnFxThread(chunk);
            appended += chunk.length();
        }

        this.flushScheduled.set(false);
        synchronized (this.pendingText) {
            if (!this.pendingText.isEmpty()) {
                this.scheduleFlush();
            }
        }
    }

    private void appendOnFxThread(final String chunk) {
        this.logArea.appendText(chunk);
        final int overflow = this.logArea.getLength() - MAX_VISIBLE_CHARS;
        if (overflow > 0) {
            this.logArea.deleteText(0, overflow);
        }
        this.logArea.positionCaret(this.logArea.getLength());
    }

    private void copyLogs() {
        final ClipboardContent content = new ClipboardContent();
        content.putString(this.logArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void clearLogs() {
        this.logArea.clear();
    }
}
