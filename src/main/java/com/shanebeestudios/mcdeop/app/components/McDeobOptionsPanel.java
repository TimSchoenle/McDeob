package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;

public class McDeobOptionsPanel extends HBox {
    private final CheckBox remapCheckBox;
    private final CheckBox decompileCheckBox;
    private final CheckBox zipCheckBox;

    public McDeobOptionsPanel() {
        super(15);
        this.setAlignment(Pos.CENTER);

        this.remapCheckBox = new CheckBox("Remap");
        this.decompileCheckBox = new CheckBox("Decompile");
        this.zipCheckBox = new CheckBox("Zip");

        this.remapCheckBox.setSelected(true);
        this.decompileCheckBox.setSelected(true);
        this.zipCheckBox.setSelected(true);

        this.getChildren().addAll(this.remapCheckBox, this.decompileCheckBox, this.zipCheckBox);
    }

    public void setRemapVisible(boolean visible) {
        this.remapCheckBox.setVisible(visible);
        if (!visible) {
            this.remapCheckBox.setSelected(false);
        }
    }

    public ProcessorOptions getOptions() {
        return ProcessorOptions.builder()
                .remap(this.remapCheckBox.isSelected())
                .decompile(this.decompileCheckBox.isSelected())
                .zipDecompileOutput(this.zipCheckBox.isSelected())
                .build();
    }

    public void setControlsDisable(boolean disable) {
        this.remapCheckBox.setDisable(disable);
        this.decompileCheckBox.setDisable(disable);
        this.zipCheckBox.setDisable(disable);
    }
}
