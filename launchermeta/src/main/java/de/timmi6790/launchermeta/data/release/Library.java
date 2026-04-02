package de.timmi6790.launchermeta.data.release;

import java.util.Optional;

public record Library(String name, LibraryArtifact artifact) {
    public Optional<LibraryArtifact> getArtifact() {
        return Optional.ofNullable(this.artifact);
    }
}
