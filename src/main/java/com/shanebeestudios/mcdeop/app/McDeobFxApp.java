package com.shanebeestudios.mcdeop.app;

import com.shanebeestudios.mcdeop.McDeob;
import com.shanebeestudios.mcdeop.VersionManager;
import com.shanebeestudios.mcdeop.app.components.*;
import com.shanebeestudios.mcdeop.processor.Processor;
import com.shanebeestudios.mcdeop.processor.ResourceRequest;
import de.timmi6790.launchermeta.data.release.ReleaseManifest;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.InputStream;
import java.util.Objects;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McDeobFxApp extends Application {
    @Setter
    private static VersionManager versionManager;

    private McDeobTypeSelection typeSelection;
    private McDeobVersionSelection versionSelection;
    private McDeobOptionsPanel optionsPanel;
    private McDeobStatusBox statusBox;
    private Button startButton;

    @Override
    public void start(final Stage stage) {
        if (versionManager == null) {
            log.error("VersionManager not initialized!");
            Platform.exit();
            return;
        }

        stage.setTitle("McDeob - " + McDeob.getVersion());
        this.setIcons(stage);

        final VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("main-panel");

        final McDeobTitle title = new McDeobTitle();

        this.typeSelection = new McDeobTypeSelection();

        // Options Panel initialized before Version Selection to avoid NPE
        this.optionsPanel = new McDeobOptionsPanel();

        this.versionSelection = new McDeobVersionSelection(versionManager);
        this.versionSelection.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                this.updateRemapVisibility(newV);
            }
        });

        this.statusBox = new McDeobStatusBox();

        this.startButton = new Button("Start!");
        this.startButton.getStyleClass().add("start-button");
        this.startButton.setPrefWidth(150);
        this.startButton.setOnAction(e -> this.handleStart());

        root.getChildren()
                .addAll(
                        title,
                        this.typeSelection,
                        this.versionSelection,
                        this.optionsPanel,
                        this.statusBox,
                        this.startButton);

        // Initial check for the already selected version (from constructor)
        final Version current = this.versionSelection.getValue();
        if (current != null) {
            this.updateRemapVisibility(current);
        }

        final Scene scene = new Scene(root, 500, 350);
        try {
            scene.getStylesheets()
                    .add(Objects.requireNonNull(this.getClass().getResource("/styles.css"))
                            .toExternalForm());
        } catch (final Exception e) {
            log.warn("Could not load styles.css", e);
        }

        stage.setScene(scene);
        stage.show();
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

    private void handleStart() {
        final Version version = this.versionSelection.getValue();
        if (version == null) {
            this.statusBox.updateStatus("Invalid Version!", true);
            return;
        }

        this.setControlsEnabled(false);
        this.statusBox.updateStatus("Fetching Manifest...", false);
        this.startButton.setText("Running...");

        final Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    final ReleaseManifest manifest = versionManager.getReleaseManifest(version);
                    final ResourceRequest request =
                            new ResourceRequest(manifest, McDeobFxApp.this.typeSelection.getSelectedType());

                    Processor.runProcessor(request, McDeobFxApp.this.optionsPanel.getOptions(), this::updateMessage);
                } catch (final Exception e) {
                    log.error("Process failed", e);
                    this.updateMessage("Failed: " + e.getMessage());
                    throw e;
                }
                return null;
            }
        };

        task.messageProperty()
                .addListener((obs, old, newMsg) -> Platform.runLater(() -> this.statusBox.setText(newMsg)));

        task.setOnSucceeded(e -> {
            this.statusBox.updateStatus("Done!", false);
            this.resetControls();
        });

        task.setOnFailed(e -> {
            this.statusBox.updateStatus("Error Occurred!", true);
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
        this.startButton.setText("Start!");
    }
}
