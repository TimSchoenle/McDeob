package com.shanebeestudios.mcdeop.processor.mache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * Reads {@code mache.json}.
 *
 * <p>Parsed through Jackson's tree model rather than data binding, matching how McDeob reads Mojang's manifests:
 * it keeps the native image free of the reflection registrations data binding would need.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MacheMetaParser {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * Argument list keys, newest first.
     *
     * <p>mache renamed this list when sculptor 2 dropped the separately declared remapper, and published builds
     * of both shapes are still resolvable, so both names are accepted.
     */
    private static final String[] CODEBOOK_ARGUMENT_KEYS = {"codebookArgs", "remapperArgs"};

    static MacheMeta parse(final byte[] json) throws IOException {
        final JsonNode root = OBJECT_MAPPER.readTree(json);
        final JsonNode dependencies = root.path("dependencies");

        return new MacheMeta(
                requiredText(root, "minecraftVersion"),
                requiredText(root, "macheVersion"),
                requiredArtifact(dependencies, "codebook"),
                optionalArtifact(dependencies, "remapper"),
                optionalArtifact(dependencies, "paramMappings"),
                optionalArtifact(dependencies, "constants"),
                requiredArtifact(dependencies, "decompiler"),
                parseRepositories(root.path("repositories")),
                parseArguments(root, CODEBOOK_ARGUMENT_KEYS),
                parseArguments(root, new String[] {"decompilerArgs"}));
    }

    private static List<MacheRepository> parseRepositories(final JsonNode node) {
        final List<MacheRepository> repositories = new ArrayList<>();
        if (!node.isArray()) {
            return repositories;
        }

        for (final JsonNode entry : node) {
            final String url = entry.path("url").asText(null);
            if (url == null || url.isBlank()) {
                continue;
            }

            final List<String> groups = new ArrayList<>();
            for (final JsonNode group : entry.path("groups")) {
                final String value = group.asText(null);
                if (value != null && !value.isBlank()) {
                    groups.add(value);
                }
            }
            repositories.add(new MacheRepository(entry.path("name").asText(url), url, groups));
        }
        return repositories;
    }

    private static List<String> parseArguments(final JsonNode root, final String[] keys) {
        for (final String key : keys) {
            final JsonNode node = root.path(key);
            if (!node.isArray()) {
                continue;
            }

            final List<String> arguments = new ArrayList<>();
            for (final JsonNode argument : node) {
                arguments.add(argument.asText());
            }
            return arguments;
        }
        return List.of();
    }

    private static MavenArtifact requiredArtifact(final JsonNode dependencies, final String key) throws IOException {
        final MavenArtifact artifact = optionalArtifact(dependencies, key);
        if (artifact == null) {
            throw new IOException("mache.json declares no '" + key + "' dependency");
        }
        return artifact;
    }

    /**
     * Reads the first entry of a dependency list.
     *
     * <p>mache models each dependency as a list, but the pipeline runs one artifact per role; any further entry
     * would be silently unused, so it is reported instead of ignored.
     */
    private static @Nullable MavenArtifact optionalArtifact(final JsonNode dependencies, final String key)
            throws IOException {
        final JsonNode node = dependencies.path(key);
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        if (node.size() > 1) {
            throw new IOException("mache.json declares " + node.size() + " '" + key
                    + "' dependencies, but McDeob can only run one of each");
        }

        final JsonNode entry = node.get(0);
        return new MavenArtifact(
                requiredText(entry, "group"),
                requiredText(entry, "name"),
                requiredText(entry, "version"),
                blankToNull(entry.path("classifier").asText(null)),
                // mache omits the extension and lets the resolver take it from the artifact's POM.
                blankToNull(entry.path("extension").asText(null)));
    }

    private static @Nullable String blankToNull(@Nullable final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requiredText(final JsonNode node, final String key) throws IOException {
        final String value = node.path(key).asText(null);
        if (value == null || value.isBlank()) {
            throw new IOException("mache.json is missing the required '" + key + "' field");
        }
        return value;
    }
}
