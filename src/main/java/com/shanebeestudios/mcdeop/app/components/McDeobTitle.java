package com.shanebeestudios.mcdeop.app.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class McDeobTitle extends VBox {
    public McDeobTitle() {
        super(4);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("title-block");

        final Label headline = new Label("Minecraft Deobfuscation");
        headline.getStyleClass().add("title-label");

        final Label subtitle = new Label("Choose target, version, and steps.");
        subtitle.getStyleClass().add("title-subtitle");
        subtitle.setWrapText(true);

        this.getChildren().addAll(headline, subtitle);
    }
}
