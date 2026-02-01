package com.shanebeestudios.mcdeop;

import com.shanebeestudios.mcdeop.processor.Processor;
import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import com.shanebeestudios.mcdeop.processor.ResourceRequest;
import com.shanebeestudios.mcdeop.processor.SourceType;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.IOException;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Command(name = "mcdeob", mixinStandardHelpOptions = true, description = "Deobfuscate Minecraft versions")
public class McDeobCommand implements Callable<Integer> {

    @Option(
            names = {"-v", "--version"},
            description = "Minecraft version for which we're deobfuscating")
    private String versionString;

    @Option(
            names = {"-t", "--type"},
            description = "What we should deobfuscate: client or server")
    private String typeString;

    @Option(
            names = {"-r", "--remap"},
            description = "Marks that we should remap the deobfuscated source")
    private boolean remap;

    @Option(
            names = {"-d", "--decompile"},
            description = "Marks that we should decompile the deobfuscated source")
    private boolean decompile;

    @Option(
            names = {"-z", "--zip"},
            description = "Marks that we should zip the decompiled source")
    private boolean zip;

    @Option(
            names = "--versions",
            description = "Prints a list of all Minecraft versions available to deobfuscate",
            help = true)
    private boolean listVersions;

    private final VersionManager versionManager;

    public McDeobCommand(final VersionManager versionManager) {
        this.versionManager = versionManager;
    }

    @Override
    public Integer call() {
        if (this.listVersions) {
            System.out.println("Available Minecraft versions to deobfuscate:");
            for (final Version version : this.versionManager.getVersions()) {
                System.out.println(" - " + version.id());
            }
            return 0;
        }

        if (this.versionString == null) {
            log.error("No version specified, shutting down...");
            return 1;
        }

        if (this.typeString == null) {
            log.error("No type specified, shutting down...");
            return 1;
        }

        final SourceType type;
        try {
            type = SourceType.valueOf(this.typeString.toUpperCase());
        } catch (final IllegalArgumentException e) {
            log.error("Invalid type specified, shutting down...");
            return 1;
        }

        this.versionManager
                .getVersion(this.versionString)
                .map(version -> {
                    try {
                        return this.versionManager.getReleaseManifest(version);
                    } catch (final IOException e) {
                        log.error("Failed to fetch release manifest", e);
                    }

                    return null;
                })
                .map(manifest -> new ResourceRequest(manifest, type))
                .ifPresentOrElse(
                        request -> {
                            boolean shouldRemap = this.remap;
                            if (shouldRemap && request.getMappings().isEmpty()) {
                                log.warn(
                                        "Mappings are not available for version {}, skipping remapping.",
                                        request.getVersion().id());
                                shouldRemap = false;
                            }

                            final ProcessorOptions processorOptions = ProcessorOptions.builder()
                                    .remap(shouldRemap)
                                    .decompile(this.decompile)
                                    .zipDecompileOutput(this.zip)
                                    .build();

                            final Thread processorThread = new Thread(
                                    () -> Processor.runProcessor(request, processorOptions, null), "Processor");
                            processorThread.start();
                            try {
                                processorThread.join();
                            } catch (final InterruptedException e) {
                                log.error("Processor interrupted", e);
                                Thread.currentThread().interrupt();
                            }
                        },
                        () -> {
                            log.error("Invalid or unsupported version was specified, shutting down...");
                            System.exit(1);
                        });

        return 0;
    }
}
