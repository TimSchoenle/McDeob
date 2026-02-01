package com.shanebeestudios.mcdeop.app.components;

import javafx.scene.control.Label;

public class McDeobTitle extends Label {
    public McDeobTitle() {
        super("Let's start de-obfuscating some Minecraft");
        this.getStyleClass().add("title-label");
    }
}
