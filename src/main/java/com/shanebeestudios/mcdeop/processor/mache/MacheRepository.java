package com.shanebeestudios.mcdeop.processor.mache;

import java.util.List;

/**
 * A Maven repository mache resolves its tooling from.
 *
 * @param name repository name, used in log output
 * @param url base URL, always ending in a slash
 * @param groups group ids this repository is allowed to serve; empty means no restriction
 */
public record MacheRepository(String name, String url, List<String> groups) {
    public MacheRepository(final String name, final String url, final List<String> groups) {
        this.name = name;
        this.url = url.endsWith("/") ? url : url + '/';
        this.groups = List.copyOf(groups);
    }

    /**
     * Whether this repository should be queried for a group.
     *
     * <p>mache declares the group of every repository it lists, so honouring the declaration avoids pointless
     * requests and keeps a stray {@code 200} from an unrelated repository out of the resolution.
     *
     * @param group the group id being resolved
     * @return {@code true} if the repository serves the group or declares no restriction
     */
    public boolean serves(final String group) {
        if (this.groups.isEmpty()) {
            return true;
        }

        for (final String served : this.groups) {
            if (group.equals(served) || group.startsWith(served + '.')) {
                return true;
            }
        }
        return false;
    }
}
