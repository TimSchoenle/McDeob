package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.util.GithubReleaseChecker;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class McDeobUpdateNotification extends VBox {
    private static final int CHANGELOG_PREVIEW_LINES = 5;
    private static final int CHANGELOG_EXPANDED_LINES = 12;
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_PATTERN = Pattern.compile("(`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*)");

    private final javafx.scene.control.Label stateBadge;
    private final javafx.scene.control.Label titleLabel;
    private final javafx.scene.control.Label detailLabel;
    private final HBox actions;
    private final VBox changelogBox;
    private final TextFlow changelogFlow;
    private final ScrollPane changelogScroll;
    private final Button changelogToggleButton;
    private boolean changelogExpanded;
    private String changelogPreviewText = "";
    private String changelogFullText = "";

    public McDeobUpdateNotification() {
        this.getStyleClass().addAll("panel-card", "update-notification");
        this.setSpacing(10);
        this.setVisible(false);
        this.setManaged(false);

        this.stateBadge = new Label();
        this.stateBadge.getStyleClass().add("update-badge");

        this.titleLabel = new Label();
        this.titleLabel.getStyleClass().add("update-title");

        this.detailLabel = new Label();
        this.detailLabel.getStyleClass().add("update-detail");
        this.detailLabel.setWrapText(true);
        this.detailLabel.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(this.detailLabel, Priority.NEVER);

        this.actions = new HBox(8);
        this.actions.getStyleClass().add("update-actions");
        this.actions.setAlignment(Pos.CENTER_LEFT);

        this.changelogFlow = new TextFlow();
        this.changelogFlow.getStyleClass().add("update-changelog-flow");
        this.changelogFlow.setLineSpacing(2.0);
        this.changelogFlow.setMaxWidth(Double.MAX_VALUE);

        this.changelogScroll = new ScrollPane(this.changelogFlow);
        this.changelogScroll.getStyleClass().add("update-changelog-scroll");
        this.changelogScroll.setFitToWidth(true);
        this.changelogScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.changelogScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        this.changelogToggleButton = new Button("Show Full Changelog");
        this.changelogToggleButton.getStyleClass().add("update-changelog-toggle");
        this.changelogToggleButton.setOnAction(event -> this.toggleChangelogMode());

        this.changelogBox = new VBox(6, this.changelogScroll, this.changelogToggleButton);
        this.changelogBox.getStyleClass().add("update-changelog-box");
        this.changelogBox.setVisible(false);
        this.changelogBox.setManaged(false);
        this.changelogBox.setFillWidth(true);
        this.changelogFlow
                .prefWidthProperty()
                .bind(this.changelogScroll.widthProperty().subtract(24));

        final HBox topRow = new HBox(10, this.stateBadge, this.titleLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getStyleClass().add("update-top-row");

        this.getChildren().addAll(topRow, this.detailLabel, this.changelogBox, this.actions);
    }

    public void showChecking() {
        this.showContainer();
        this.refreshStyle("checking");
        this.stateBadge.setText("Checking");
        this.titleLabel.setText("Checking for updates...");
        this.detailLabel.setText("Contacting GitHub to compare the installed version with the latest release.");
        this.actions.getChildren().setAll();
        this.hideChangelog();
    }

    public void showUpToDate(final String currentVersion, final String checkedAtText, final Runnable onCheckAgain) {
        this.showContainer();
        this.refreshStyle("up-to-date");
        this.stateBadge.setText("Current");
        this.titleLabel.setText("You are on the latest version (" + currentVersion + ")");
        this.detailLabel.setText(checkedAtText);
        this.actions
                .getChildren()
                .setAll(
                        this.buildActionButton("Check Again", onCheckAgain, false),
                        this.buildActionButton("Dismiss", this::dismiss, false));
        this.hideChangelog();
    }

    public void showUpdateAvailable(
            final String currentVersion,
            final GithubReleaseChecker.UpdateInfo info,
            final Runnable onOpenRelease,
            final Runnable onDismiss) {
        this.showContainer();
        this.refreshStyle("available");
        this.stateBadge.setText("Update");
        this.titleLabel.setText("New version available: " + info.latestTag());
        final boolean hasDistinctName = info.releaseName() != null
                && !info.releaseName().isBlank()
                && !info.releaseName().trim().equalsIgnoreCase(info.latestTag().trim());
        final String nameText = hasDistinctName ? " - " + info.releaseName().trim() : "";
        this.detailLabel.setText("Installed: " + currentVersion + " | Latest: " + info.latestTag() + nameText);
        this.actions
                .getChildren()
                .setAll(
                        this.buildActionButton("Open", onOpenRelease, true),
                        this.buildActionButton("Dismiss", onDismiss, false));
        this.showChangelog(info.releaseBody());
    }

    public void showCheckFailed(final String reason, final Runnable onRetry, final Runnable onDismiss) {
        this.showContainer();
        this.refreshStyle("failed");
        this.stateBadge.setText("Issue");
        this.titleLabel.setText("Unable to check for updates");
        this.detailLabel.setText(reason);
        this.actions
                .getChildren()
                .setAll(
                        this.buildActionButton("Retry", onRetry, true),
                        this.buildActionButton("Dismiss", onDismiss, false));
        this.hideChangelog();
    }

    public void dismiss() {
        this.setVisible(false);
        this.setManaged(false);
    }

    private Button buildActionButton(final String text, final Runnable action, final boolean primary) {
        final Button button = new Button(text);
        button.getStyleClass().add("update-action-button");
        if (primary) {
            button.getStyleClass().add("update-action-primary");
        }
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private void showContainer() {
        this.setVisible(true);
        this.setManaged(true);
    }

    private void refreshStyle(final String stateClass) {
        this.getStyleClass()
                .removeAll(
                        "update-state-checking",
                        "update-state-up-to-date",
                        "update-state-available",
                        "update-state-failed");
        this.getStyleClass().add("update-state-" + stateClass);
    }

    private void showChangelog(final String rawReleaseBody) {
        final String normalized = this.normalizeChangelog(rawReleaseBody);
        if (normalized.isBlank()) {
            this.hideChangelog();
            return;
        }
        final String preview = this.buildPreview(normalized);
        this.changelogPreviewText = preview;
        this.changelogFullText = normalized;
        this.changelogExpanded = false;
        this.renderMarkdown(this.changelogPreviewText);
        this.changelogToggleButton.setText("Show Full Changelog");
        this.applyChangelogViewportHeight(false);
        this.changelogBox.setVisible(true);
        this.changelogBox.setManaged(true);
    }

    private void hideChangelog() {
        this.changelogExpanded = false;
        this.changelogPreviewText = "";
        this.changelogFullText = "";
        this.changelogFlow.getChildren().clear();
        this.changelogToggleButton.setText("Show Full Changelog");
        this.changelogBox.setVisible(false);
        this.changelogBox.setManaged(false);
        this.applyChangelogViewportHeight(false);
    }

    private String buildPreview(final String fullText) {
        final String[] lines = fullText.split("\\R");
        final StringBuilder out = new StringBuilder();
        final int previewCount = Math.min(CHANGELOG_PREVIEW_LINES, lines.length);
        for (int i = 0; i < previewCount; i++) {
            if (i > 0) {
                out.append(System.lineSeparator());
            }
            out.append(lines[i]);
        }
        if (lines.length > CHANGELOG_PREVIEW_LINES) {
            out.append(System.lineSeparator()).append("...");
        }
        return out.toString();
    }

    private String normalizeChangelog(final String releaseBody) {
        if (releaseBody == null) {
            return "";
        }
        final String normalized = releaseBody.replace("\r\n", "\n");
        final String[] lines = normalized.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].trim().isEmpty()) {
            end--;
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i]);
        }
        return out.toString().trim();
    }

    private void applyChangelogViewportHeight(final boolean expanded) {
        final double lineHeight = this.computeLineHeight();
        final int maxLines = expanded ? CHANGELOG_EXPANDED_LINES : CHANGELOG_PREVIEW_LINES;
        final String activeText = expanded ? this.changelogFullText : this.changelogPreviewText;
        final int visibleLines = Math.max(1, Math.min(maxLines, this.countLines(activeText)));
        this.changelogScroll.setPrefViewportHeight(Math.ceil(lineHeight * visibleLines) + 14.0);
        this.changelogScroll.setVbarPolicy(
                expanded ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER);
    }

    private void toggleChangelogMode() {
        this.changelogExpanded = !this.changelogExpanded;
        this.renderMarkdown(this.changelogExpanded ? this.changelogFullText : this.changelogPreviewText);
        this.changelogToggleButton.setText(this.changelogExpanded ? "Show Preview" : "Show Full Changelog");
        this.applyChangelogViewportHeight(this.changelogExpanded);
        this.changelogScroll.setVvalue(0.0);
    }

    private double computeLineHeight() {
        final double fontSize = 12.0;
        return Math.max(18.0, fontSize * 1.45);
    }

    private int countLines(final String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        return text.split("\\R", -1).length;
    }

    private void renderMarkdown(final String markdown) {
        this.changelogFlow.getChildren().clear();
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        final String[] lines = markdown.split("\\n", -1);
        boolean inCodeBlock = false;
        for (final String line : lines) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                this.appendStyledLine(line, 12, true, false, true);
                continue;
            }
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                this.changelogFlow.getChildren().add(new Text("\n"));
                continue;
            }
            if (trimmed.startsWith("### ")) {
                this.appendInlineMarkdown(trimmed.substring(4), 13, true, false, false, true);
                continue;
            }
            if (trimmed.startsWith("## ")) {
                this.appendInlineMarkdown(trimmed.substring(3), 14, true, false, false, true);
                continue;
            }
            if (trimmed.startsWith("# ")) {
                this.appendInlineMarkdown(trimmed.substring(2), 15, true, false, false, true);
                continue;
            }
            if (trimmed.matches("^[-*+]\\s+.*")) {
                this.appendInlineMarkdown("\u2022 " + trimmed.substring(2), 12, false, false, false, true);
                continue;
            }
            this.appendInlineMarkdown(trimmed, 12, false, false, false, true);
        }
    }

    private void appendStyledLine(
            final String value, final int size, final boolean bold, final boolean italic, final boolean code) {
        this.appendText(value, size, bold, italic, code);
        this.changelogFlow.getChildren().add(new Text("\n"));
    }

    private void appendInlineMarkdown(
            final String rawLine,
            final int size,
            final boolean defaultBold,
            final boolean defaultItalic,
            final boolean defaultCode,
            final boolean newline) {
        final String line = this.normalizeInlineLinks(rawLine);
        final Matcher matcher = INLINE_PATTERN.matcher(line);
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() > index) {
                this.appendText(line.substring(index, matcher.start()), size, defaultBold, defaultItalic, defaultCode);
            }
            final String token = matcher.group();
            if (token.startsWith("**") && token.endsWith("**")) {
                this.appendText(token.substring(2, token.length() - 2), size, true, defaultItalic, defaultCode);
            } else if (token.startsWith("*") && token.endsWith("*")) {
                this.appendText(token.substring(1, token.length() - 1), size, defaultBold, true, defaultCode);
            } else if (token.startsWith("`") && token.endsWith("`")) {
                this.appendText(token.substring(1, token.length() - 1), size, defaultBold, defaultItalic, true);
            } else {
                this.appendText(token, size, defaultBold, defaultItalic, defaultCode);
            }
            index = matcher.end();
        }
        if (index < line.length()) {
            this.appendText(line.substring(index), size, defaultBold, defaultItalic, defaultCode);
        }
        if (newline) {
            this.changelogFlow.getChildren().add(new Text("\n"));
        }
    }

    private void appendText(
            final String value, final int size, final boolean bold, final boolean italic, final boolean code) {
        if (value.isEmpty()) {
            return;
        }
        final Text text = new Text(value);
        if (code) {
            text.setStyle("-fx-font-family: \"Cascadia Code\", \"Consolas\", monospace; -fx-fill: #1e3950;");
        } else {
            text.setStyle("-fx-fill: #1e3950;");
        }
        text.setFont(Font.font(
                code ? "Cascadia Code" : "Bahnschrift",
                bold ? FontWeight.BOLD : FontWeight.NORMAL,
                italic ? FontPosture.ITALIC : FontPosture.REGULAR,
                size));
        this.changelogFlow.getChildren().add(text);
    }

    private String normalizeInlineLinks(final String line) {
        final Matcher matcher = LINK_PATTERN.matcher(line);
        final StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + " (" + matcher.group(2) + ")"));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
