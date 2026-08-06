package com.shanebeestudios.mcdeop.processor.mache;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** A Maven repository's {@code maven-metadata.xml}. */
final class MavenMetadata {
    private final String document;

    private MavenMetadata(final String document) {
        this.document = document;
    }

    static MavenMetadata of(final byte[] bytes) {
        return new MavenMetadata(new String(bytes, StandardCharsets.UTF_8));
    }

    /** Every version listed under {@code <versioning><versions>}, in the order the repository published them. */
    List<String> versions() {
        final String versions = XmlElements.first(this.document, "versions");
        return versions == null ? List.of() : XmlElements.all(versions, "version");
    }

    /**
     * Resolves the timestamped file version of a snapshot.
     *
     * <p>Snapshot directories hold one file per build, named after a timestamp rather than after
     * {@code -SNAPSHOT}, and the mapping between the two lives only in this document.
     *
     * @param extension the artifact's packaging extension
     * @param classifier the artifact's classifier, or {@code null} for the main artifact
     * @return the version to use in the file name, or empty if the snapshot does not publish that artifact
     */
    Optional<String> snapshotVersion(final String extension, @Nullable final String classifier) {
        final String snapshotVersions = XmlElements.first(this.document, "snapshotVersions");
        if (snapshotVersions == null) {
            return Optional.empty();
        }

        for (final String entry : XmlElements.all(snapshotVersions, "snapshotVersion")) {
            if (!extension.equals(XmlElements.first(entry, "extension"))) {
                continue;
            }

            final String entryClassifier = XmlElements.first(entry, "classifier");
            final boolean classifierMatches =
                    classifier == null ? entryClassifier == null : classifier.equals(entryClassifier);
            if (classifierMatches) {
                return Optional.ofNullable(XmlElements.first(entry, "value"));
            }
        }

        return Optional.empty();
    }
}
