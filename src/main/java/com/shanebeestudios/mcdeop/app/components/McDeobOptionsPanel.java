package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;

public class McDeobOptionsPanel extends FlowPane {
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

        this.getChildren()
                .addAll(
                        this.remapCheckBox,
                        this.decompileCheckBox,
                        this.zipCheckBox,
                        this.librariesCheckBox,
                        this.gradleProjectCheckBox);
    }

    private void configureOption(final CheckBox box) {
        box.getStyleClass().add("option-chip");
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
                .build();
    }

    public void setControlsDisable(final boolean disable) {
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
