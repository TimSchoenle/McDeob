package com.shanebeestudios.mcdeop.app;

import com.shanebeestudios.mcdeop.VersionManager;
import com.shanebeestudios.mcdeop.app.components.McDeobLogWindow;
import com.shanebeestudios.mcdeop.app.components.McDeobOptionsPanel;
import com.shanebeestudios.mcdeop.app.components.McDeobStatusBox;
import com.shanebeestudios.mcdeop.app.components.McDeobTitle;
import com.shanebeestudios.mcdeop.app.components.McDeobTypeSelection;
import com.shanebeestudios.mcdeop.app.components.McDeobVersionSelection;
import com.shanebeestudios.mcdeop.processor.Processor;
import com.shanebeestudios.mcdeop.processor.ResourceRequest;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import com.shanebeestudios.mcdeop.util.GithubReleaseChecker;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McDeobFxApp extends Application {
    private static final String GITHUB_REPO_URL = "https://github.com/Timmi6790/McDeob";
    private static final String GITHUB_RELEASES_URL = GITHUB_REPO_URL + "/releases/latest";

    @Setter
    private static VersionManager versionManager;

    private final GithubReleaseChecker releaseChecker = new GithubReleaseChecker();

    private McDeobTypeSelection typeSelection;
    private McDeobVersionSelection versionSelection;
    private McDeobOptionsPanel optionsPanel;
    private McDeobStatusBox statusBox;
    private McDeobLogWindow logWindow;
    private Button startButton;
    private Button updateButton;
    private String latestReleaseUrl = GITHUB_RELEASES_URL;

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

        final VBox controlsCard = new VBox(14);
        controlsCard.setFillWidth(true);
        controlsCard.getStyleClass().add("panel-card");
        controlsCard.getChildren()
                .addAll(
                        this.createFieldRow("Target", this.typeSelection),
                        this.createFieldRow("Minecraft Version", this.versionSelection),
                        this.createFieldRow("Pipeline Steps", this.optionsPanel));

        this.statusBox = new McDeobStatusBox();
        HBox.setHgrow(this.statusBox, Priority.ALWAYS);

        this.startButton = new Button("Start Processing");
        this.startButton.getStyleClass().add("start-button");
        this.startButton.setPrefHeight(40);
        this.startButton.setMaxWidth(Double.MAX_VALUE);
        this.startButton.setOnAction(e -> this.handleStart());

        final HBox statusRow = new HBox(this.statusBox);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("action-row");

        final HBox startRow = new HBox(this.startButton);
        HBox.setHgrow(this.startButton, Priority.ALWAYS);
        startRow.setAlignment(Pos.CENTER_LEFT);
        startRow.getStyleClass().add("start-row");

        this.logWindow = new McDeobLogWindow();
        final VBox logCard = new VBox(this.logWindow);
        logCard.getStyleClass().addAll("panel-card", "log-panel");
        VBox.setVgrow(this.logWindow, Priority.ALWAYS);
        VBox.setVgrow(logCard, Priority.ALWAYS);

        root.getChildren().addAll(headerRow, controlsCard, statusRow, startRow, logCard);

        // Initial check for the already selected version (from constructor)
        final Version current = this.versionSelection.getValue();
        if (current != null) {
            this.updateRemapVisibility(current);
        }

        final Scene scene = new Scene(root, 900, 680);
        try {
            scene.getStylesheets()
                    .add(Objects.requireNonNull(this.getClass().getResource("/styles.css"))
                            .toExternalForm());
        } catch (final Exception e) {
            log.warn("Could not load styles.css", e);
        }

        stage.setMinWidth(780);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
        this.animateEntrance(headerRow, controlsCard, statusRow, startRow, logCard);
        this.checkForUpdatesAsync();
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
        githubButton.setOnAction(e -> this.getHostServices().showDocument(GITHUB_REPO_URL));

        this.updateButton = this.createIconButton("\u2B06");
        this.updateButton.getStyleClass().add("update-icon-button");
        this.updateButton.setVisible(false);
        this.updateButton.setManaged(false);
        this.updateButton.setOnAction(e -> this.getHostServices().showDocument(this.latestReleaseUrl));

        final HBox iconActions = new HBox(8, githubButton, this.updateButton);
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

    private void checkForUpdatesAsync() {
        final Task<Optional<GithubReleaseChecker.UpdateInfo>> task = new Task<>() {
            @Override
            protected Optional<GithubReleaseChecker.UpdateInfo> call() {
                try {
                    return McDeobFxApp.this.releaseChecker.checkForUpdate(GeneratedConstant.VERSION);
                } catch (final Exception e) {
                    log.debug("Could not check for newer GitHub releases", e);
                    return Optional.empty();
                }
            }
        };

        task.setOnSucceeded(event -> {
            final Optional<GithubReleaseChecker.UpdateInfo> updateInfo = task.getValue();
            if (updateInfo.isPresent()) {
                final GithubReleaseChecker.UpdateInfo info = updateInfo.get();
                final String url = info.releaseUrl();
                if (url != null && !url.isBlank()) {
                    this.latestReleaseUrl = url;
                }
                this.updateButton.setVisible(true);
                this.updateButton.setManaged(true);
            }
        });

        final Thread thread = new Thread(task, "Release-Check-Thread");
        thread.setDaemon(true);
        thread.start();
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

    private void handleStart() {
        final Version version = this.versionSelection.getValue();
        if (version == null) {
            this.statusBox.updateStatus("Invalid Version!", true);
            return;
        }

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
        this.typeSelection.setControlsDisable(!enabled);
        this.optionsPanel.setControlsDisable(!enabled);
        this.startButton.setDisable(!enabled);
    }

    private void resetControls() {
        this.setControlsEnabled(true);
        this.startButton.setText("Start Processing");
    }
}
