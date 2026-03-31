package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.VersionManager;
import de.timmi6790.launchermeta.data.version.Version;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

public class McDeobVersionSelection extends ComboBox<Version> {

    public McDeobVersionSelection(VersionManager versionManager) {
        this.setPromptText("Select Version");
        this.setPrefWidth(280);
        this.setMaxWidth(Double.MAX_VALUE);
        this.setVisibleRowCount(18);
        this.getStyleClass().add("version-selection");
        this.getItems().addAll(versionManager.getVersions());

        // Custom cell factory to show IDs
        this.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Version item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.id());
                }
            }
        });
        this.setButtonCell(this.getCellFactory().call(null));

        if (!this.getItems().isEmpty()) {
            this.getSelectionModel().selectFirst();
        }
    }
}
