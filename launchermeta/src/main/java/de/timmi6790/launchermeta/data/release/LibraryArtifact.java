package de.timmi6790.launchermeta.data.release;

import java.net.URL;

public record LibraryArtifact(String path, String sha1, long size, URL url) {}
