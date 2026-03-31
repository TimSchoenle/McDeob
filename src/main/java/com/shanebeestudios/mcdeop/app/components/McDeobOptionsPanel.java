package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;

public class McDeobOptionsPanel extends FlowPane {
    private final CheckBox remapCheckBox;
    private final CheckBox decompileCheckBox;
    private final CheckBox zipCheckBox;

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

        this.configureOption(this.remapCheckBox);
        this.configureOption(this.decompileCheckBox);
        this.configureOption(this.zipCheckBox);

        this.remapCheckBox.setSelected(true);
        this.decompileCheckBox.setSelected(true);
        this.zipCheckBox.setSelected(true);

        this.getChildren().addAll(this.remapCheckBox, this.decompileCheckBox, this.zipCheckBox);
    }

    private void configureOption(final CheckBox box) {
        box.getStyleClass().add("option-chip");
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
                .zipDecompileOutput(this.zipCheckBox.isSelected())
                .build();
    }

    public void setControlsDisable(final boolean disable) {
        this.remapCheckBox.setDisable(disable);
        this.decompileCheckBox.setDisable(disable);
        this.zipCheckBox.setDisable(disable);
    }
}
