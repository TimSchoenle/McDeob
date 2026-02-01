package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.SourceType;
import javafx.geometry.Pos;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

public class McDeobTypeSelection extends HBox {
    private final RadioButton clientRadio;
    private final RadioButton serverRadio;

    public McDeobTypeSelection() {
        super(20);
        this.setAlignment(Pos.CENTER);

        ToggleGroup typeGroup = new ToggleGroup();
        this.clientRadio = new RadioButton("Client");
        this.clientRadio.setToggleGroup(typeGroup);
        this.clientRadio.setSelected(true);

        this.serverRadio = new RadioButton("Server");
        this.serverRadio.setToggleGroup(typeGroup);

        this.getChildren().addAll(this.clientRadio, this.serverRadio);
    }

    public SourceType getSelectedType() {
        return this.serverRadio.isSelected() ? SourceType.SERVER : SourceType.CLIENT;
    }

    public void setControlsDisable(boolean disable) {
        this.clientRadio.setDisable(disable);
        this.serverRadio.setDisable(disable);
    }
}
