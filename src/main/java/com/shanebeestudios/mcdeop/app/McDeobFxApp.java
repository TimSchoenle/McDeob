package com.shanebeestudios.mcdeop.app;

import com.shanebeestudios.mcdeop.VersionManager;
import com.shanebeestudios.mcdeop.app.components.McDeobLogWindow;
import com.shanebeestudios.mcdeop.app.components.McDeobOptionsPanel;
import com.shanebeestudios.mcdeop.app.components.McDeobStatusBox;
import com.shanebeestudios.mcdeop.app.components.McDeobTitle;
import com.shanebeestudios.mcdeop.app.components.McDeobTypeSelection;
import com.shanebeestudios.mcdeop.app.components.McDeobUpdateNotification;
import com.shanebeestudios.mcdeop.app.components.McDeobVersionSelection;
import com.shanebeestudios.mcdeop.processor.Processor;
import com.shanebeestudios.mcdeop.processor.ResourceRequest;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import com.shanebeestudios.mcdeop.util.GithubReleaseChecker;
import com.shanebeestudios.mcdeop.util.Util;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McDeobFxApp extends Application {
    private static final String GITHUB_RELEASES_URL = GeneratedConstant.GITHUB_REPO_URL + "/releases/latest";
    private static final DateTimeFormatter CHECKED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Setter
    private static VersionManager versionManager;

    private final GithubReleaseChecker releaseChecker = new GithubReleaseChecker();

    private McDeobTypeSelection typeSelection;
    private McDeobVersionSelection versionSelection;
    private McDeobOptionsPanel optionsPanel;
    private McDeobStatusBox statusBox;
    private McDeobUpdateNotification updateNotification;
    private McDeobLogWindow logWindow;
    private Button startButton;
    private Button openDirectoryButton;
    private Button checkUpdatesButton;
    private Path lastOutputDirectory;
    private String latestReleaseUrl = GITHUB_RELEASES_URL;
    private boolean updateCheckInProgress;

    @Override
    public void start(final Stage stage) {
        if (versionManager == null) {
            log.error("VersionManager not initialized!");
            Platform.exit();
            return;
        }

        stage.setTitle("McDeob - " + GeneratedConstant.VERSION);
        this.setIcons(stage);

        final VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_LEFT);
        root.setFillWidth(true);
        root.getStyleClass().add("app-shell");

        final McDeobTitle title = new McDeobTitle();
        final HBox iconActions = this.createIconActions();
        final HBox headerRow = new HBox(12, title, iconActions);
        headerRow.setAlignment(Pos.TOP_LEFT);
        headerRow.getStyleClass().add("header-row");
        HBox.setHgrow(title, Priority.ALWAYS);

        this.updateNotification = new McDeobUpdateNotification();

        this.typeSelection = new McDeobTypeSelection();
        this.typeSelection.addSelectionListener(() -> {
            final Version selectedVersion = this.versionSelection != null ? this.versionSelection.getValue() : null;
            if (selectedVersion != null) {
                this.updateRemapVisibility(selectedVersion);
            }
        });

        // Options Panel initialized before Version Selection to avoid NPE
        this.optionsPanel = new McDeobOptionsPanel();

        this.versionSelection = new McDeobVersionSelection(versionManager);
        this.versionSelection.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                this.updateRemapVisibility(newV);
            }
        });
        final VBox versionSelectionRow = new VBox(8);
        versionSelectionRow.setAlignment(Pos.CENTER_LEFT);
        versionSelectionRow.setMaxWidth(Region.USE_PREF_SIZE);
        versionSelectionRow
                .getChildren()
                .addAll(this.versionSelection.getVersionTypeSelectionControl(), this.versionSelection);

        final VBox controlsCard = new VBox(14);
        controlsCard.setFillWidth(true);
        controlsCard.getStyleClass().add("panel-card");
        controlsCard
                .getChildren()
                .addAll(
                        this.createFieldRow("Target", this.typeSelection),
                        this.createFieldRow("Minecraft Version", versionSelectionRow),
                        this.createFieldRow("Pipeline Steps", this.optionsPanel));

        this.statusBox = new McDeobStatusBox();
        HBox.setHgrow(this.statusBox, Priority.ALWAYS);

        this.startButton = new Button("Start Processing");
        this.startButton.getStyleClass().add("start-button");
        this.startButton.setPrefHeight(40);
        this.startButton.setMaxWidth(Double.MAX_VALUE);
        this.startButton.setOnAction(e -> this.handleStart());

        this.openDirectoryButton = new Button("Open Output Folder");
        this.openDirectoryButton.getStyleClass().add("ghost-button");
        this.openDirectoryButton.setPrefHeight(40);
        this.openDirectoryButton.setDisable(true);
        this.openDirectoryButton.setOnAction(e -> this.openOutputDirectory());

        final HBox statusRow = new HBox(this.statusBox);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("action-row");

        final HBox startRow = new HBox(10, this.startButton, this.openDirectoryButton);
        HBox.setHgrow(this.startButton, Priority.ALWAYS);
        startRow.setAlignment(Pos.CENTER_LEFT);
        startRow.getStyleClass().add("start-row");

        this.logWindow = new McDeobLogWindow();
        final VBox logCard = new VBox(this.logWindow);
        logCard.getStyleClass().addAll("panel-card", "log-panel");
        VBox.setVgrow(this.logWindow, Priority.ALWAYS);
        VBox.setVgrow(logCard, Priority.ALWAYS);

        root.getChildren().addAll(headerRow, this.updateNotification, controlsCard, statusRow, startRow, logCard);

        // Initial check for the already selected version (from constructor)
        final Version current = this.versionSelection.getValue();
        if (current != null) {
            this.updateRemapVisibility(current);
        }

        final ScrollPane appScroll = new ScrollPane(root);
        appScroll.getStyleClass().add("app-scroll");
        appScroll.setFitToWidth(true);
        appScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        appScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        appScroll.setPannable(true);
        root.prefWidthProperty().bind(appScroll.widthProperty().subtract(20));

        final Scene scene = new Scene(appScroll, 900, 680);
        try {
            scene.getStylesheets()
                    .add(Objects.requireNonNull(this.getClass().getResource("/styles.css"))
                            .toExternalForm());
        } catch (final Exception e) {
            log.warn("Could not load styles.css", e);
        }

        stage.setScene(scene);
        this.configureStageSize(stage, scene, root, headerRow, controlsCard, statusRow, startRow, logCard);
        stage.show();
        this.animateEntrance(headerRow, controlsCard, statusRow, startRow, logCard);
        this.checkForUpdatesAsync(false);
    }

    @Override
    public void stop() {
        if (this.logWindow != null) {
            this.logWindow.dispose();
        }
    }

    private void setIcons(final Stage stage) {
        try {
            final InputStream is = this.getClass().getResourceAsStream("/images/1024.png");
            if (is != null) {
                stage.getIcons().add(new Image(is));
            }
        } catch (final Exception e) {
            log.warn("Could not load app icon", e);
        }
    }

    private void updateRemapVisibility(final Version version) {
        this.optionsPanel.setRemapVisible(versionManager.hasMappings(version));
    }

    private HBox createFieldRow(final String labelText, final Node content) {
        final Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        label.setMinWidth(140);

        final HBox row = new HBox(14, label, content);
        row.getStyleClass().add("field-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);
        return row;
    }

    private HBox createIconActions() {
        final Button githubButton = this.createIconButton("\u2197");
        githubButton.setOnAction(e -> this.getHostServices().showDocument(GeneratedConstant.GITHUB_REPO_URL));

        this.checkUpdatesButton = this.createIconButton("\u21BB");
        this.checkUpdatesButton.getStyleClass().add("update-icon-button");
        this.checkUpdatesButton.setOnAction(e -> this.checkForUpdatesAsync(true));

        final HBox iconActions = new HBox(8, githubButton, this.checkUpdatesButton);
        iconActions.setAlignment(Pos.CENTER_RIGHT);
        iconActions.getStyleClass().add("icon-actions");
        return iconActions;
    }

    private Button createIconButton(final String symbol) {
        final Button button = new Button(symbol);
        button.getStyleClass().add("icon-button");
        button.setFocusTraversable(false);
        button.setMinSize(34, 34);
        button.setPrefSize(34, 34);
        button.setMaxSize(34, 34);
        return button;
    }

    private void checkForUpdatesAsync(final boolean userInitiated) {
        if (this.updateCheckInProgress) {
            return;
        }
        this.updateCheckInProgress = true;
        this.checkUpdatesButton.setDisable(true);
        this.updateNotification.showChecking();

        final Task<GithubReleaseChecker.UpdateCheckResult> task = new Task<>() {
            @Override
            protected GithubReleaseChecker.UpdateCheckResult call() {
                try {
                    return McDeobFxApp.this.releaseChecker.checkForUpdateDetailed(GeneratedConstant.VERSION);
                } catch (final Exception e) {
                    log.error("Could not check for newer GitHub releases", e);
                    return new GithubReleaseChecker.UpdateCheckResult(
                            GithubReleaseChecker.UpdateCheckStatus.FAILED,
                            null,
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getSimpleName());
                }
            }
        };

        task.setOnSucceeded(event -> {
            this.updateCheckInProgress = false;
            this.checkUpdatesButton.setDisable(false);
            final GithubReleaseChecker.UpdateCheckResult result = task.getValue();
            if (result == null) {
                this.updateNotification.showCheckFailed(
                        "No response received from update checker.",
                        () -> this.checkForUpdatesAsync(true),
                        this.updateNotification::dismiss);
                return;
            }

            if (result.status() == GithubReleaseChecker.UpdateCheckStatus.UPDATE_AVAILABLE
                    && result.updateInfo() != null) {
                final String url = result.updateInfo().releaseUrl();
                if (url != null && !url.isBlank()) {
                    this.latestReleaseUrl = url;
                }
                this.updateNotification.showUpdateAvailable(
                        GeneratedConstant.VERSION,
                        result.updateInfo(),
                        this::openLatestRelease,
                        this.updateNotification::dismiss);
                return;
            }

            if (result.status() == GithubReleaseChecker.UpdateCheckStatus.UP_TO_DATE) {
                if (userInitiated) {
                    final String checkedAt = "Last checked: " + CHECKED_AT_FORMAT.format(LocalDateTime.now());
                    this.updateNotification.showUpToDate(
                            GeneratedConstant.VERSION, checkedAt, () -> this.checkForUpdatesAsync(true));
                } else {
                    this.updateNotification.dismiss();
                }
                return;
            }

            final String reason =
                    result.errorMessage() != null && !result.errorMessage().isBlank()
                            ? result.errorMessage()
                            : "Unknown error while contacting GitHub.";
            this.updateNotification.showCheckFailed(
                    reason, () -> this.checkForUpdatesAsync(true), this.updateNotification::dismiss);
        });

        task.setOnFailed(event -> {
            this.updateCheckInProgress = false;
            this.checkUpdatesButton.setDisable(false);
            final Throwable throwable = task.getException();
            final String reason = throwable != null && throwable.getMessage() != null
                    ? throwable.getMessage()
                    : "Unexpected failure while checking updates.";
            this.updateNotification.showCheckFailed(
                    reason, () -> this.checkForUpdatesAsync(true), this.updateNotification::dismiss);
        });

        final Thread thread = new Thread(task, "Release-Check-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void openLatestRelease() {
        this.getHostServices().showDocument(this.latestReleaseUrl);
    }

    private void animateEntrance(final Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            final Node node = nodes[i];
            node.setOpacity(0);
            node.setTranslateY(18);

            final Duration delay = Duration.millis(i * 80.0);

            final FadeTransition fadeTransition = new FadeTransition(Duration.millis(380), node);
            fadeTransition.setFromValue(0);
            fadeTransition.setToValue(1);
            fadeTransition.setDelay(delay);

            final TranslateTransition translateTransition = new TranslateTransition(Duration.millis(420), node);
            translateTransition.setFromY(18);
            translateTransition.setToY(0);
            translateTransition.setDelay(delay);

            fadeTransition.play();
            translateTransition.play();
        }
    }

    private void configureStageSize(
            final Stage stage,
            final Scene scene,
            final VBox root,
            final Node headerRow,
            final Node controlsCard,
            final Node statusRow,
            final Node startRow,
            final Node logCard) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        final double horizontalPadding =
                root.getPadding().getLeft() + root.getPadding().getRight();
        final double contentMinWidth = Math.max(
                Math.max(headerRow.prefWidth(-1), controlsCard.prefWidth(-1)),
                Math.max(statusRow.prefWidth(-1), Math.max(startRow.prefWidth(-1), logCard.prefWidth(-1))));
        final double computedMinWidth = contentMinWidth + horizontalPadding + 56;

        final double constrainedContentWidth = Math.max(contentMinWidth, 760);
        final double computedMinHeight = root.prefHeight(constrainedContentWidth) + 56;

        final Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        final double monitorWidth = visualBounds.getWidth();
        final double monitorHeight = visualBounds.getHeight();

        final double minWidth = Math.min(computedMinWidth, monitorWidth);
        final double minHeight = Math.min(computedMinHeight, monitorHeight);

        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        stage.setWidth(Math.min(Math.max(scene.getWidth(), minWidth), monitorWidth));
        stage.setHeight(Math.min(Math.max(scene.getHeight(), minHeight), monitorHeight));
    }

    private void handleStart() {
        final Version version = this.versionSelection.getValue();
        if (version == null) {
            this.statusBox.updateStatus("Invalid Version!", true);
            return;
        }
        this.lastOutputDirectory = this.resolveOutputDirectory(version);

        this.setControlsEnabled(false);
        this.statusBox.updateRunningMessage("Fetching release manifest...");
        this.startButton.setText("Processing...");

        final Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    final ReleaseManifest manifest = versionManager.getReleaseManifest(version);
                    final ResourceRequest request =
                            new ResourceRequest(manifest, McDeobFxApp.this.typeSelection.getSelectedType());

                    final boolean success = Processor.runProcessor(
                            request, McDeobFxApp.this.optionsPanel.getOptions(), this::updateMessage);
                    if (!success) {
                        this.updateMessage("Failed to complete processing");
                        throw new IllegalStateException("Processor failed");
                    }
                } catch (final Exception e) {
                    log.error("Process failed", e);
                    this.updateMessage("Failed: " + e.getMessage());
                    throw e;
                }
                return null;
            }
        };

        task.messageProperty().addListener((obs, old, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                Platform.runLater(() -> this.statusBox.updateRunningMessage(newMsg));
            }
        });

        task.setOnSucceeded(e -> {
            this.statusBox.updateStatus("Completed successfully!", false);
            this.openDirectoryButton.setDisable(false);
            this.resetControls();
        });

        task.setOnFailed(e -> {
            this.statusBox.updateStatus("Process failed. Review logs for details.", true);
            this.resetControls();
        });

        new Thread(task, "Processor-Thread").start();
    }

    private void setControlsEnabled(final boolean enabled) {
        this.versionSelection.setDisable(!enabled);
        this.versionSelection.setVersionTypeSelectionDisable(!enabled);
        this.typeSelection.setControlsDisable(!enabled);
        this.optionsPanel.setControlsDisable(!enabled);
        this.startButton.setDisable(!enabled);
        this.openDirectoryButton.setDisable(!enabled || this.lastOutputDirectory == null);
    }

    private void resetControls() {
        this.setControlsEnabled(true);
        this.startButton.setText("Start Processing");
    }

    private Path resolveOutputDirectory(final Version version) {
        final String versionFolder = String.format(
                "%s-%s", this.typeSelection.getSelectedType().name().toLowerCase(Locale.ENGLISH), version.id());
        return Util.getBaseDataFolder().resolve(versionFolder).toAbsolutePath();
    }

    private void openOutputDirectory() {
        if (this.lastOutputDirectory == null) {
            this.statusBox.updateStatus("No output directory is available yet.", true);
            return;
        }
        if (!Files.isDirectory(this.lastOutputDirectory)) {
            this.statusBox.updateStatus("Output directory not found: " + this.lastOutputDirectory, true);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            this.statusBox.updateStatus("Desktop integration is not supported on this system.", true);
            return;
        }
        try {
            Desktop.getDesktop().open(this.lastOutputDirectory.toFile());
        } catch (final IOException exception) {
            log.error("Failed to open output directory {}", this.lastOutputDirectory, exception);
            this.statusBox.updateStatus("Failed to open output directory.", true);
        }
    }
}
