package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.processor.PipelineType;
import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

public class McDeobOptionsPanel extends FlowPane {
    private static final DecompilerType DEFAULT_DECOMPILER = DecompilerType.VINEFLOWER;
    private static final PipelineType DEFAULT_PIPELINE = PipelineType.MOJANG;

    private static final String MACHE_TOOLTIP = """
            PaperMC mache runs codebook, unpicks constants and applies mache's patches, \
            producing named parameters and source that compiles.
            Server versions PaperMC supports only. Needs Java 21, which is downloaded if this machine has none.\
            """;

    private final ComboBox<PipelineType> pipelineComboBox;
    private final ComboBox<DecompilerType> decompilerComboBox;
    private final CheckBox remapCheckBox;
    private final CheckBox decompileCheckBox;
    private final CheckBox zipCheckBox;
    private final CheckBox librariesCheckBox;
    private final CheckBox gradleProjectCheckBox;

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
        this.librariesCheckBox = new CheckBox("Libraries");
        this.gradleProjectCheckBox = new CheckBox("Gradle Project");
        this.pipelineComboBox = this.createPipelineComboBox();
        this.decompilerComboBox = this.createDecompilerComboBox();

        this.configureOption(this.remapCheckBox);
        this.configureOption(this.decompileCheckBox);
        this.configureOption(this.zipCheckBox);
        this.configureOption(this.librariesCheckBox);
        this.configureOption(this.gradleProjectCheckBox);

        this.remapCheckBox.setSelected(true);
        this.decompileCheckBox.setSelected(true);
        this.zipCheckBox.setSelected(true);
        this.librariesCheckBox.setSelected(false);
        this.gradleProjectCheckBox.setSelected(false);

        this.decompileCheckBox.selectedProperty().addListener((obs, oldV, newV) -> this.updateDependencies());
        this.gradleProjectCheckBox.selectedProperty().addListener((obs, oldV, newV) -> this.updateDependencies());
        this.pipelineComboBox.valueProperty().addListener((obs, oldV, newV) -> this.updateDependencies());
        this.updateDependencies();

        this.getChildren()
                .addAll(
                        this.createComboRow("Pipeline", this.pipelineComboBox, "pipeline-row", "pipeline-label"),
                        this.createComboRow(
                                "Decompiler", this.decompilerComboBox, "decompiler-row", "decompiler-label"),
                        this.remapCheckBox,
                        this.decompileCheckBox,
                        this.zipCheckBox,
                        this.librariesCheckBox,
                        this.gradleProjectCheckBox);
    }

    private void configureOption(final CheckBox box) {
        box.getStyleClass().add("option-chip");
    }

    private HBox createComboRow(
            final String labelText, final ComboBox<?> comboBox, final String rowStyle, final String labelStyle) {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(rowStyle);

        final Label label = new Label(labelText);
        label.getStyleClass().add(labelStyle);
        row.getChildren().addAll(label, comboBox);
        return row;
    }

    private ComboBox<PipelineType> createPipelineComboBox() {
        final ComboBox<PipelineType> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(PipelineType.values());
        comboBox.getSelectionModel().select(DEFAULT_PIPELINE);
        comboBox.setPrefWidth(170);
        comboBox.setMaxWidth(220);
        comboBox.getStyleClass().add("pipeline-selection");
        comboBox.setTooltip(new Tooltip(MACHE_TOOLTIP));
        return comboBox;
    }

    private ComboBox<DecompilerType> createDecompilerComboBox() {
        final ComboBox<DecompilerType> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(DecompilerType.values());
        comboBox.getSelectionModel().select(DEFAULT_DECOMPILER);
        comboBox.setPrefWidth(170);
        comboBox.setMaxWidth(220);
        comboBox.getStyleClass().add("decompiler-selection");
        return comboBox;
    }

    /**
     * Keeps the controls consistent with what the selected pipeline actually reads.
     *
     * <p>Mache always remaps, always decompiles, chooses its own decompiler and takes its libraries from the
     * server bundler, so those controls are switched off rather than left to imply an effect they do not have.
     */
    private void updateDependencies() {
        final boolean mache = this.isMacheSelected();
        final boolean gradleSelected = this.gradleProjectCheckBox.isSelected();

        if (gradleSelected && !mache) {
            this.decompileCheckBox.setSelected(true);
            this.librariesCheckBox.setSelected(true);
        }

        if (!mache && !this.decompileCheckBox.isSelected()) {
            this.zipCheckBox.setSelected(false);
        }

        this.zipCheckBox.setDisable(!mache && !this.decompileCheckBox.isSelected());
        this.remapCheckBox.setDisable(mache);
        this.decompileCheckBox.setDisable(mache || gradleSelected);
        this.librariesCheckBox.setDisable(mache || gradleSelected);
        this.decompilerComboBox.setDisable(mache || !this.decompileCheckBox.isSelected());
    }

    public void setRemapVisible(final boolean visible) {
        this.remapCheckBox.setVisible(visible);
        this.remapCheckBox.setManaged(visible);
        this.remapCheckBox.setSelected(visible);
        this.updateDependencies();
    }

    /**
     * Offers or withdraws the mache pipeline.
     *
     * <p>Mache only covers server jars, so the choice is removed rather than left selectable and rejected once
     * processing has already started.
     *
     * @param supported whether mache can run for the current target
     */
    public void setMacheSupported(final boolean supported) {
        if (supported) {
            if (this.pipelineComboBox.getItems().size() != PipelineType.values().length) {
                this.pipelineComboBox.getItems().setAll(PipelineType.values());
                this.pipelineComboBox.getSelectionModel().select(DEFAULT_PIPELINE);
            }
            return;
        }

        this.pipelineComboBox.getItems().setAll(PipelineType.MOJANG);
        this.pipelineComboBox.getSelectionModel().select(PipelineType.MOJANG);
        this.updateDependencies();
    }

    private boolean isMacheSelected() {
        return this.pipelineComboBox.getValue() == PipelineType.MACHE;
    }

    public ProcessorOptions getOptions() {
        final boolean mache = this.isMacheSelected();
        return ProcessorOptions.builder()
                .remap(this.remapCheckBox.isSelected())
                .decompile(this.decompileCheckBox.isSelected())
                .zipDecompileOutput((mache || this.decompileCheckBox.isSelected()) && this.zipCheckBox.isSelected())
                .downloadLibraries(this.librariesCheckBox.isSelected() && !mache)
                .setupGradleProject(this.gradleProjectCheckBox.isSelected())
                .decompilerType(
                        this.decompilerComboBox.getValue() == null
                                ? DEFAULT_DECOMPILER
                                : this.decompilerComboBox.getValue())
                .pipelineType(
                        this.pipelineComboBox.getValue() == null ? DEFAULT_PIPELINE : this.pipelineComboBox.getValue())
                .build();
    }

    public void setControlsDisable(final boolean disable) {
        this.pipelineComboBox.setDisable(disable);
        this.decompilerComboBox.setDisable(disable);
        this.remapCheckBox.setDisable(disable);
        this.decompileCheckBox.setDisable(disable);
        this.zipCheckBox.setDisable(disable);
        this.librariesCheckBox.setDisable(disable);
        this.gradleProjectCheckBox.setDisable(disable);

        if (!disable) {
            this.updateDependencies();
        }
    }
}
