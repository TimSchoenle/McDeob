package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

public class McDeobOptionsPanel extends FlowPane {
    private static final DecompilerType DEFAULT_DECOMPILER = DecompilerType.VINEFLOWER;

    private final ComboBox<DecompilerType> decompilerComboBox;
    private final CheckBox remapCheckBox;
    private final CheckBox decompileCheckBox;
    private final CheckBox zipCheckBox;
    private final CheckBox librariesCheckBox;
    private final CheckBox gradleProjectCheckBox;

    public McDeobOptionsPanel() {
        super(10, 10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("options-panel");
        this.setMaxWidth(Double.MAX_VALUE);
        this.getStyleClass().add("options-flow");
        this.prefWrapLengthProperty().bind(this.widthProperty());

        this.remapCheckBox = new CheckBox("Remap");
        this.decompileCheckBox = new CheckBox("Decompile");
        this.zipCheckBox = new CheckBox("Zip");
        this.librariesCheckBox = new CheckBox("Libraries");
        this.gradleProjectCheckBox = new CheckBox("Gradle Project");
        this.decompilerComboBox = this.createDecompilerComboBox();

        this.configureOption(this.remapCheckBox);
        this.configureOption(this.decompileCheckBox);
        this.configureOption(this.zipCheckBox);
        this.configureOption(this.librariesCheckBox);
        this.configureOption(this.gradleProjectCheckBox);

        this.remapCheckBox.setSelected(true);
        this.decompileCheckBox.setSelected(true);
        this.zipCheckBox.setSelected(true);
        this.librariesCheckBox.setSelected(false);
        this.gradleProjectCheckBox.setSelected(false);

        this.decompileCheckBox.selectedProperty().addListener((obs, oldV, newV) -> this.updateDependencies());
        this.gradleProjectCheckBox.selectedProperty().addListener((obs, oldV, newV) -> this.updateDependencies());
        this.updateDependencies();

        final HBox decompilerRow = new HBox(8);
        decompilerRow.setAlignment(Pos.CENTER_LEFT);
        decompilerRow.getStyleClass().add("decompiler-row");
        final Label decompilerLabel = new Label("Decompiler");
        decompilerLabel.getStyleClass().add("decompiler-label");
        decompilerRow.getChildren().addAll(decompilerLabel, this.decompilerComboBox);

        this.getChildren()
                .addAll(
                        decompilerRow,
                        this.remapCheckBox,
                        this.decompileCheckBox,
                        this.zipCheckBox,
                        this.librariesCheckBox,
                        this.gradleProjectCheckBox);
    }

    private void configureOption(final CheckBox box) {
        box.getStyleClass().add("option-chip");
    }

    private ComboBox<DecompilerType> createDecompilerComboBox() {
        final ComboBox<DecompilerType> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(DecompilerType.values());
        comboBox.getSelectionModel().select(DEFAULT_DECOMPILER);
        comboBox.setPrefWidth(170);
        comboBox.setMaxWidth(220);
        comboBox.getStyleClass().add("decompiler-selection");
        return comboBox;
    }

    private void updateDependencies() {
        final boolean gradleSelected = this.gradleProjectCheckBox.isSelected();
        if (gradleSelected) {
            this.decompileCheckBox.setSelected(true);
            this.librariesCheckBox.setSelected(true);
        }

        if (!this.decompileCheckBox.isSelected()) {
            this.zipCheckBox.setSelected(false);
        }

        this.zipCheckBox.setDisable(!this.decompileCheckBox.isSelected());
        this.decompileCheckBox.setDisable(gradleSelected);
        this.librariesCheckBox.setDisable(gradleSelected);
        this.decompilerComboBox.setDisable(!this.decompileCheckBox.isSelected());
    }

    public void setRemapVisible(final boolean visible) {
        this.remapCheckBox.setVisible(visible);
        this.remapCheckBox.setManaged(visible);
        this.remapCheckBox.setSelected(visible);
    }

    public ProcessorOptions getOptions() {
        return ProcessorOptions.builder()
                .remap(this.remapCheckBox.isSelected())
                .decompile(this.decompileCheckBox.isSelected())
                .zipDecompileOutput(this.decompileCheckBox.isSelected() && this.zipCheckBox.isSelected())
                .downloadLibraries(this.librariesCheckBox.isSelected())
                .setupGradleProject(this.gradleProjectCheckBox.isSelected())
                .decompilerType(
                        this.decompilerComboBox.getValue() == null
                                ? DEFAULT_DECOMPILER
                                : this.decompilerComboBox.getValue())
                .build();
    }

    public void setControlsDisable(final boolean disable) {
        this.decompilerComboBox.setDisable(disable);
        this.remapCheckBox.setDisable(disable);
        this.decompileCheckBox.setDisable(disable);
        this.zipCheckBox.setDisable(disable);
        this.librariesCheckBox.setDisable(disable);
        this.gradleProjectCheckBox.setDisable(disable);

        if (!disable) {
            this.updateDependencies();
        }
    }
}
