package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.SourceType;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

public class McDeobTypeSelection extends HBox {
    private final ToggleGroup typeGroup = new ToggleGroup();
    private final RadioButton clientRadio;
    private final RadioButton serverRadio;

    public McDeobTypeSelection() {
        super(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("segmented-group");

        this.clientRadio = this.createOption("Client Jar");
        this.clientRadio.setSelected(true);
        this.serverRadio = this.createOption("Server Jar");

        this.getChildren().addAll(this.clientRadio, this.serverRadio);
    }

    private RadioButton createOption(final String label) {
        final RadioButton radio = new RadioButton(label);
        radio.setToggleGroup(this.typeGroup);
        radio.setAlignment(Pos.CENTER);
        radio.setContentDisplay(ContentDisplay.TEXT_ONLY);
        radio.getStyleClass().add("segmented-option");
        return radio;
    }

    public SourceType getSelectedType() {
        return this.serverRadio.isSelected() ? SourceType.SERVER : SourceType.CLIENT;
    }

    /**
     * Registers a listener notified once per selection change.
     *
     * <p>Bound to the toggle group rather than to each radio's {@code selectedProperty}: switching
     * option flips two properties, so per-radio listeners fired twice for a single user action.
     *
     * @param runnable invoked after the selected type changes
     */
    public void addSelectionListener(final Runnable runnable) {
        this.typeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> runnable.run());
    }

    public void setControlsDisable(final boolean disable) {
        this.clientRadio.setDisable(disable);
        this.serverRadio.setDisable(disable);
    }
}
