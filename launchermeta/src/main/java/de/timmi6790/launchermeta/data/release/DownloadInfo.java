package de.timmi6790.launchermeta.data.release;

import java.net.URL;

public record DownloadInfo(String sha1, long size, URL url) {}
