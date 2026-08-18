package com.shanebeestudios.mcdeop.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Util {
    public static boolean isRunningMacOS() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("mac");
    }

    public static Path getBaseDataFolder() {
        if (Util.isRunningMacOS()) {
            // If running on macOS, put the output directory in the user home directory.
            // This is due to how macOS APPs work - their '.' directory resolves to one inside the APP itself.
            return Paths.get(System.getProperty("user.home"), "McDeob");
        }

        return Paths.get("versions");
    }

    /**
     * Hints that now is a good moment to collect, after a stage that drops hundreds of megabytes.
     *
     * <p>Only a hint: the previous implementation spun on {@link System#gc()} until a weak reference
     * cleared, which never terminates under {@code -XX:+DisableExplicitGC} or a no-op collector, and
     * blocked the caller for an unbounded time under any collector.
     */
    public static void suggestGC() {
        System.gc();
    }
}
