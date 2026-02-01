package com.shanebeestudios.mcdeop.app.components;

import javafx.geometry.Pos;
import javafx.scene.control.TextField;

public class McDeobStatusBox extends TextField {

    public McDeobStatusBox() {
        super("Ready");
        this.setEditable(false);
        this.setAlignment(Pos.CENTER);
        this.setMaxWidth(500);
        this.getStyleClass().add("status-box");
    }

    public void updateStatus(String msg, boolean isError) {
        this.setText(msg);
        if (isError) {
            this.setStyle("-fx-text-fill: red;");
        } else {
            this.setStyle("");
        }
    }
}
