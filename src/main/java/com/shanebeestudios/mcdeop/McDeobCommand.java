package com.shanebeestudios.mcdeop;

import com.shanebeestudios.mcdeop.processor.Processor;
import com.shanebeestudios.mcdeop.processor.ProcessorOptions;
import com.shanebeestudios.mcdeop.processor.ResourceRequest;
import com.shanebeestudios.mcdeop.processor.SourceType;
import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import de.timmi6790.launchermeta.data.version.Version;
import java.io.IOException;
import java.util.Optional;
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
            required = true,
            description = "What we should deobfuscate: ${COMPLETION-CANDIDATES}")
    private SourceType type;

    @Option(
            names = "--remap",
            negatable = true,
            defaultValue = "true",
            description = "Remap the obfuscated source (default: ${DEFAULT-VALUE})")
    private boolean remap = true;

    @Option(
            names = "--decompile",
            negatable = true,
            defaultValue = "true",
            description = "Decompile classes into source files (default: ${DEFAULT-VALUE})")
    private boolean decompile = true;

    @Option(
            names = "--zip",
            negatable = true,
            defaultValue = "true",
            description = "Zip decompiled output (default: ${DEFAULT-VALUE})")
    private boolean zip = true;

    @Option(
            names = "--decompiler",
            defaultValue = "vineflower",
            description =
                    "Decompiler engine to use (supported: vineflower, fernflower, cfr, jadx; default: ${DEFAULT-VALUE})")
    private String decompiler;

    @Option(
            names = "--libraries",
            negatable = true,
            defaultValue = "false",
            description = "Download all release libraries (default: ${DEFAULT-VALUE})")
    private boolean libraries;

    @Option(
            names = "--gradle-project",
            negatable = true,
            defaultValue = "false",
            description =
                    "Generate a base Gradle project using decompiled sources and downloaded libraries (default: ${DEFAULT-VALUE})")
    private boolean gradleProject;

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

        final Optional<DecompilerType> decompilerTypeResult = DecompilerType.fromValue(this.decompiler);
        if (decompilerTypeResult.isEmpty()) {
            log.error(
                    "Invalid decompiler was specified ({}). Supported values: {}",
                    this.decompiler,
                    DecompilerType.supportedValues());
            return 1;
        }
        final DecompilerType decompilerType = decompilerTypeResult.get();

        final Optional<Version> versionResult = this.versionManager.getVersion(this.versionString);
        if (versionResult.isEmpty()) {
            log.error("Invalid or unsupported version was specified, shutting down...");
            return 1;
        }
        final Version version = versionResult.get();

        final ResourceRequest request;
        try {
            request = new ResourceRequest(this.versionManager.getReleaseManifest(version), this.type);
        } catch (final IOException e) {
            log.error("Failed to fetch release manifest", e);
            return 1;
        }

        boolean shouldRemap = this.remap;
        if (shouldRemap && request.getMappings().isEmpty()) {
            log.warn(
                    "Mappings are not available for version {}, skipping remapping.",
                    request.getVersion().id());
            shouldRemap = false;
        }

        if (this.gradleProject && !this.decompile) {
            log.error("--gradle-project requires --decompile");
            return 1;
        }

        if (this.gradleProject && !this.libraries) {
            log.error("--gradle-project requires --libraries");
            return 1;
        }

        final ProcessorOptions processorOptions = ProcessorOptions.builder()
                .remap(shouldRemap)
                .decompile(this.decompile)
                .zipDecompileOutput(this.zip && this.decompile)
                .downloadLibraries(this.libraries)
                .setupGradleProject(this.gradleProject)
                .decompilerType(decompilerType)
                .build();

        return Processor.runProcessor(request, processorOptions, null) ? 0 : 1;
    }
}
