package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.SourceType;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

public class McDeobTypeSelection extends HBox {
    private final RadioButton clientRadio;
    private final RadioButton serverRadio;

    public McDeobTypeSelection() {
        super(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("segmented-group");

        final ToggleGroup typeGroup = new ToggleGroup();
        this.clientRadio = new RadioButton("Client Jar");
        this.clientRadio.setToggleGroup(typeGroup);
        this.clientRadio.setSelected(true);
        this.clientRadio.setAlignment(Pos.CENTER);
        this.clientRadio.setContentDisplay(ContentDisplay.TEXT_ONLY);
        this.clientRadio.getStyleClass().add("segmented-option");

        this.serverRadio = new RadioButton("Server Jar");
        this.serverRadio.setToggleGroup(typeGroup);
        this.serverRadio.setAlignment(Pos.CENTER);
        this.serverRadio.setContentDisplay(ContentDisplay.TEXT_ONLY);
        this.serverRadio.getStyleClass().add("segmented-option");

        this.getChildren().addAll(this.clientRadio, this.serverRadio);
    }

    public SourceType getSelectedType() {
        return this.serverRadio.isSelected() ? SourceType.SERVER : SourceType.CLIENT;
    }

    public void addSelectionListener(final Runnable runnable) {
        this.clientRadio.selectedProperty().addListener((obs, oldValue, newValue) -> runnable.run());
        this.serverRadio.selectedProperty().addListener((obs, oldValue, newValue) -> runnable.run());
    }

    public void setControlsDisable(final boolean disable) {
        this.clientRadio.setDisable(disable);
        this.serverRadio.setDisable(disable);
    }
}
