package com.shanebeestudios.mcdeop.app.components;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;

public class McDeobStatusBox extends TextField {
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final PseudoClass RUNNING_STATE = PseudoClass.getPseudoClass("running");

    public McDeobStatusBox() {
        super("Ready to process");
        this.setEditable(false);
        this.setAlignment(Pos.CENTER);
        this.setMaxWidth(Double.MAX_VALUE);
        this.setPrefHeight(40);
        this.setFocusTraversable(false);
        this.getStyleClass().add("status-box");
    }

    public void updateStatus(final String msg, final boolean isError) {
        this.setText(msg);
        this.pseudoClassStateChanged(RUNNING_STATE, false);
        this.pseudoClassStateChanged(ERROR_STATE, isError);
    }

    public void updateRunningMessage(final String msg) {
        this.setText(msg);
        this.pseudoClassStateChanged(ERROR_STATE, false);
        this.pseudoClassStateChanged(RUNNING_STATE, true);
    }
}
