package com.shanebeestudios.mcdeop;

import com.shanebeestudios.mcdeop.app.McDeobFxApp;
import com.shanebeestudios.mcdeop.util.JavaLogBridge;
import de.timmi6790.RequestModule;
import de.timmi6790.launchermeta.LauncherMeta;
import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
public class McDeob {
    public static void main(final String[] args) {
        if (args.length == 0) {
            JavaLogBridge.install();
            // Native Windows builds can hit JavaFX accessibility linkage issues.
            // Keep accessibility disabled unless explicitly forced by the user.
            System.setProperty("javafx.accessible.force", "false");
            System.setProperty("glass.accessible.force", "false");
        }

        final LauncherMeta launcherMeta = new LauncherMeta(RequestModule.createHttpClient());
        final VersionManager versionManager = new VersionManager(launcherMeta);

        if (args.length == 0) {
            McDeobFxApp.setVersionManager(versionManager);
            Application.launch(McDeobFxApp.class, args);
            return;
        }

        final int exitCode = new CommandLine(new McDeobCommand(versionManager)).execute(args);
        System.exit(exitCode);
    }
}
