package com.shanebeestudios.mcdeop.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Opens paths in the platform's file manager. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DesktopLauncher {

    /**
     * Opens a directory in the platform's file manager.
     *
     * @param directory directory to reveal
     * @return {@code true} if a file manager was launched
     */
    public static boolean openDirectory(final Path directory) {
        final List<String> command = fileManagerCommand(directory);
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (final IOException exception) {
            log.warn("Failed to open directory using command {}", command, exception);
            return false;
        }
    }

    private static List<String> fileManagerCommand(final Path directory) {
        final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        final String target = directory.toString();

        if (osName.contains("win")) {
            return List.of("explorer.exe", target);
        }
        if (osName.contains("mac")) {
            return List.of("open", target);
        }
        return List.of("xdg-open", target);
    }
}
