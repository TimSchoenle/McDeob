package com.shanebeestudios.mcdeop.processor.mache;

import org.jetbrains.annotations.Nullable;

/**
 * A Maven artifact as declared by mache's metadata.
 *
 * @param group group id
 * @param name artifact id
 * @param version version, possibly a {@code -SNAPSHOT}
 * @param classifier classifier such as {@code all}, or {@code null} for the main artifact
 * @param extension packaging extension, or {@code null} when it is whatever the artifact's POM declares
 */
public record MavenArtifact(
        String group,
        String name,
        String version,
        @Nullable String classifier,
        @Nullable String extension) {
    public static final String DEFAULT_EXTENSION = "jar";
    public static final String POM_EXTENSION = "pom";

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    public static MavenArtifact of(final String group, final String name, final String version) {
        return new MavenArtifact(group, name, version, null, null);
    }

    public MavenArtifact withExtension(final String extension) {
        return new MavenArtifact(this.group, this.name, this.version, this.classifier, extension);
    }

    /** The group id as a repository path segment. */
    public String groupPath() {
        return this.group.replace('.', '/');
    }

    /** The repository directory holding this artifact's version, without a trailing slash. */
    public String versionPath() {
        return this.groupPath() + '/' + this.name + '/' + this.version;
    }

    /**
     * The artifact's file name.
     *
     * @param resolvedVersion the version to embed in the file name; for snapshots this is the timestamped version
     *     from the repository's metadata rather than {@link #version()}
     * @return the file name, for example {@code codebook-cli-1.0.18-all.jar}
     */
    public String fileName(final String resolvedVersion) {
        final StringBuilder builder = new StringBuilder(this.name).append('-').append(resolvedVersion);
        if (this.classifier != null && !this.classifier.isBlank()) {
            builder.append('-').append(this.classifier);
        }
        return builder.append('.')
                .append(this.extension == null ? DEFAULT_EXTENSION : this.extension)
                .toString();
    }

    /** The file name of this artifact's POM, which never carries a classifier. */
    public String pomFileName(final String resolvedVersion) {
        return this.name + '-' + resolvedVersion + '.' + POM_EXTENSION;
    }

    public boolean isSnapshot() {
        return this.version.endsWith(SNAPSHOT_SUFFIX);
    }

    /** Human-readable coordinates for logs and error messages. */
    public String coordinates() {
        final StringBuilder builder = new StringBuilder(this.group)
                .append(':')
                .append(this.name)
                .append(':')
                .append(this.version);
        if (this.classifier != null && !this.classifier.isBlank()) {
            builder.append(':').append(this.classifier);
        }
        return builder.toString();
    }
}
