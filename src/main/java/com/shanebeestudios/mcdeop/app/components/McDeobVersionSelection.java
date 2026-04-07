package com.shanebeestudios.mcdeop.app.components;

import com.shanebeestudios.mcdeop.VersionManager;
import de.timmi6790.launchermeta.data.version.Version;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class McDeobVersionSelection extends ComboBox<Version> {
    private static final double MIN_SELECTION_WIDTH = 420;
    private static final double PREF_SELECTION_WIDTH = 600;
    private static final double MAX_SELECTION_WIDTH = 640;
    private static final double ID_COLUMN_WIDTH = 220;
    private static final double TYPE_COLUMN_WIDTH = 180;
    private static final double RELEASE_TIME_COLUMN_WIDTH = 170;

    private static final Pattern PRE_RELEASE_PATTERN =
            Pattern.compile(".*(?:-|_)pre(?:-?release)?-?\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASE_CANDIDATE_PATTERN =
            Pattern.compile(".*(?:-|_)rc-?\\d+$", Pattern.CASE_INSENSITIVE);

    private final List<Version> allVersions;
    private final DateTimeFormatter releaseTimeFormatter;
    private final Set<DisplayVersionType> selectedVersionTypes = new LinkedHashSet<>();
    private final HBox versionTypeSelectionControl = new HBox(8);
    private final EnumMap<DisplayVersionType, CheckBox> versionTypePoints = new EnumMap<>(DisplayVersionType.class);

    public McDeobVersionSelection(final VersionManager versionManager) {
        this.setPromptText("Select Version");
        this.setPrefWidth(PREF_SELECTION_WIDTH);
        this.setMinWidth(MIN_SELECTION_WIDTH);
        this.setMaxWidth(MAX_SELECTION_WIDTH);
        this.setVisibleRowCount(18);
        this.getStyleClass().add("version-selection");
        this.allVersions = List.copyOf(versionManager.getVersions());
        this.releaseTimeFormatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());

        this.versionTypeSelectionControl.getStyleClass().add("version-type-point-selection");
        this.initializeVersionTypePoints();
        this.refreshVisibleVersions();

        this.setCellFactory(param -> this.createVersionTableCell(false));
        this.setButtonCell(this.createVersionTableCell(true));
    }

    public Node getVersionTypeSelectionControl() {
        return this.versionTypeSelectionControl;
    }

    public void setVersionTypeSelectionDisable(final boolean disable) {
        this.versionTypeSelectionControl.setDisable(disable);
    }

    private void initializeVersionTypePoints() {
        final List<DisplayVersionType> availableVersionTypes = this.getAvailableVersionTypes();
        for (final DisplayVersionType versionType : availableVersionTypes) {
            final CheckBox point = new CheckBox(versionType.label());
            point.getStyleClass().add("version-type-point");
            point.getStyleClass().add("version-type-point-" + versionType.cssSuffix());
            point.setSelected(true);
            this.selectedVersionTypes.add(versionType);
            point.selectedProperty().addListener((obs, oldValue, selected) -> {
                this.updateVersionTypeSelection(versionType, selected);
            });
            this.versionTypePoints.put(versionType, point);
            this.versionTypeSelectionControl.getChildren().add(point);
        }
    }

    private List<DisplayVersionType> getAvailableVersionTypes() {
        return this.allVersions.stream()
                .map(McDeobVersionSelection::resolveDisplayType)
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    private void updateVersionTypeSelection(final DisplayVersionType versionType, final boolean selected) {
        if (selected) {
            this.selectedVersionTypes.add(versionType);
        } else {
            this.selectedVersionTypes.remove(versionType);
        }
        this.refreshVisibleVersions();
    }

    private void refreshVisibleVersions() {
        final Version currentVersion = this.getValue();
        final List<Version> filteredVersions = this.allVersions.stream()
                .filter(version -> this.selectedVersionTypes.contains(resolveDisplayType(version)))
                .toList();

        this.getItems().setAll(filteredVersions);

        if (currentVersion != null && filteredVersions.contains(currentVersion)) {
            this.getSelectionModel().select(currentVersion);
        } else if (!this.getItems().isEmpty()) {
            this.getSelectionModel().selectFirst();
        } else {
            this.getSelectionModel().clearSelection();
        }
    }

    private ListCell<Version> createVersionTableCell(final boolean compact) {
        return new ListCell<>() {
            private final HBox row = new HBox(10);
            private final Label idLabel = new Label();
            private final Label typeLabel = new Label();
            private final Label releaseTimeLabel = new Label();

            {
                this.idLabel.getStyleClass().add("version-id");
                this.idLabel.getStyleClass().add("version-col-id");
                this.idLabel.setMinWidth(ID_COLUMN_WIDTH);
                this.idLabel.setPrefWidth(ID_COLUMN_WIDTH);
                this.idLabel.setMaxWidth(ID_COLUMN_WIDTH);
                this.idLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                this.typeLabel.getStyleClass().add("version-type");
                this.typeLabel.getStyleClass().add("version-col-type");
                this.typeLabel.setMinWidth(TYPE_COLUMN_WIDTH);
                this.typeLabel.setPrefWidth(TYPE_COLUMN_WIDTH);
                this.typeLabel.setMaxWidth(TYPE_COLUMN_WIDTH);

                this.releaseTimeLabel.getStyleClass().add("version-release-time");
                this.releaseTimeLabel.getStyleClass().add("version-col-time");
                this.releaseTimeLabel.setMinWidth(RELEASE_TIME_COLUMN_WIDTH);
                this.releaseTimeLabel.setPrefWidth(RELEASE_TIME_COLUMN_WIDTH);
                this.releaseTimeLabel.setMaxWidth(RELEASE_TIME_COLUMN_WIDTH);
                this.releaseTimeLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(this.releaseTimeLabel, Priority.NEVER);

                this.row.getStyleClass().add(compact ? "version-table-row-compact" : "version-table-row");
                this.row.getChildren().addAll(this.idLabel, this.typeLabel, this.releaseTimeLabel);
            }

            @Override
            protected void updateItem(final Version item, final boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    this.setText(null);
                    this.setGraphic(null);
                    return;
                }

                final DisplayVersionType displayType = resolveDisplayType(item);
                this.idLabel.setText(item.id());
                this.typeLabel.setText(displayType.label());
                updateVariantClass(this.typeLabel, "version-type-variant-", displayType.cssSuffix());
                this.releaseTimeLabel.setText(
                        McDeobVersionSelection.this.releaseTimeFormatter.format(item.releaseTime()));
                this.setText(null);
                this.setGraphic(this.row);
                this.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        };
    }

    private static DisplayVersionType resolveDisplayType(final Version version) {
        return switch (version.type()) {
            case SNAPSHOT -> resolveSnapshotSubtype(version);
            case RELEASE -> DisplayVersionType.RELEASE;
            case OLD_BETA -> DisplayVersionType.OLD_BETA;
            case OLD_ALPHA -> DisplayVersionType.OLD_ALPHA;
        };
    }

    private static DisplayVersionType resolveSnapshotSubtype(final Version version) {
        final String id = version.id().toLowerCase(Locale.ENGLISH);
        if (PRE_RELEASE_PATTERN.matcher(id).matches()) {
            return DisplayVersionType.PRE_RELEASE;
        }
        if (RELEASE_CANDIDATE_PATTERN.matcher(id).matches()) {
            return DisplayVersionType.RELEASE_CANDIDATE;
        }
        if (isAprilFoolsVersion(version)) {
            return DisplayVersionType.APRIL_FOOLS;
        }
        return DisplayVersionType.SNAPSHOT;
    }

    private static boolean isAprilFoolsVersion(final Version version) {
        return version.releaseTime().getMonth() == Month.APRIL
                && version.releaseTime().getDayOfMonth() == 1;
    }

    private static void updateVariantClass(final Label label, final String prefix, final String suffix) {
        label.getStyleClass().removeIf(styleClass -> styleClass.startsWith(prefix));
        label.getStyleClass().add(prefix + suffix);
    }

    private enum DisplayVersionType {
        RELEASE("Release", "release"),
        SNAPSHOT("Snapshot", "snapshot"),
        PRE_RELEASE("Pre-Release", "pre"),
        RELEASE_CANDIDATE("Release Candidate", "rc"),
        APRIL_FOOLS("April Fools", "april-fools"),
        OLD_BETA("Old Beta", "old-beta"),
        OLD_ALPHA("Old Alpha", "old-alpha");

        private final String label;
        private final String cssSuffix;

        DisplayVersionType(final String label, final String cssSuffix) {
            this.label = label;
            this.cssSuffix = cssSuffix;
        }

        public String label() {
            return this.label;
        }

        public String cssSuffix() {
            return this.cssSuffix;
        }
    }
}
