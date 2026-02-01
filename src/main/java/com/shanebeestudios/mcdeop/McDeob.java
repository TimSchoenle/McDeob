package com.shanebeestudios.mcdeop;

import com.shanebeestudios.mcdeop.app.McDeobFxApp;
import io.sentry.Sentry;
import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
public class McDeob {
    public static void main(final String[] args) {
        Sentry.init(options ->
                options.setDsn("https://a431c07b469cad98e4933270c602fb0d@o165625.ingest.sentry.io/4506099651444736"));

        final VersionManager versionManager = DaggerMcDebobComponent.create().getVersionManager();

        if (args.length == 0) {
            McDeobFxApp.setVersionManager(versionManager);
            Application.launch(McDeobFxApp.class, args);
        } else {
            final int exitCode = new CommandLine(new McDeobCommand(versionManager)).execute(args);
            System.exit(exitCode);
        }
    }

    public static String getVersion() {
        String version = McDeob.class.getPackage().getImplementationVersion();

        // The version is not available when running in an IDE
        if (version == null) {
            version = "0.0.0";
        }

        return version;
    }
}
