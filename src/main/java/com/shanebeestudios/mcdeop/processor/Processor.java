package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.decompiler.Decompiler;
import com.shanebeestudios.mcdeop.processor.decompiler.DecompilerType;
import com.shanebeestudios.mcdeop.processor.remapper.ReconstructRemapper;
import com.shanebeestudios.mcdeop.processor.remapper.Remapper;
import com.shanebeestudios.mcdeop.util.DurationTracker;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.Util;
import de.timmi6790.RequestModule;
import de.timmi6790.launchermeta.data.release.LibraryArtifact;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class Processor {
    private static final int DEFAULT_GRADLE_JAVA_VERSION = 21;
    private static final String FOOJAY_RESOLVER_PLUGIN_VERSION = "1.0.0";

    private final ResourceRequest request;
    private final ProcessorOptions options;

    @Nullable private final ResponseConsumer responseConsumer;

    private final OkHttpClient httpClient;
    private final Remapper remapper;
    private final Decompiler decompiler;

    private final Path jarPath;
    private final Path mappingsPath;
    private final Path remappedJar;
    private final Path decompiledJarPath;
    private final Path decompiledZipPath;
    private final Path librariesPath;
    private final Path gradleProjectPath;

    private Processor(
            final ResourceRequest request,
            final ProcessorOptions options,
            @Nullable final ResponseConsumer responseConsumer) {
        this.request = request;
        this.options = options;

        this.responseConsumer = responseConsumer;
        this.remapper = new ReconstructRemapper();
        this.decompiler = Optional.ofNullable(this.options.decompilerType())
                .orElse(DecompilerType.VINEFLOWER)
                .createDecompiler();
        this.httpClient = RequestModule.createHttpClient();

        final Path dataFolderPath = this.getDataFolder();
        this.jarPath = dataFolderPath.resolve("source.jar");
        this.mappingsPath = dataFolderPath.resolve("mappings.txt");
        this.remappedJar = dataFolderPath.resolve("remapped.jar");
        this.decompiledJarPath = dataFolderPath.resolve("decompiled");
        this.decompiledZipPath = dataFolderPath.resolve(Path.of("decompiled.zip"));
        this.librariesPath = dataFolderPath.resolve("libraries");
        this.gradleProjectPath = dataFolderPath.resolve("gradle-project");
    }

    public static boolean runProcessor(
            final ResourceRequest request,
            final ProcessorOptions options,
            @Nullable final ResponseConsumer responseConsumer) {
        try {
            final Processor processor = new Processor(request, options, responseConsumer);
            final boolean success = processor.init();
            processor.cleanup();
            return success;
        } catch (final Exception e) {
            log.error("Failed to run processor", e);
            return false;
        } finally {
            Util.forceGC();
        }
    }

    private Path getDataFolder() {
        final String versionFolder = String.format(
                "%s-%s",
                this.request.type().name().toLowerCase(Locale.ENGLISH),
                this.request.getVersion().id());
        final Path folderPath = Util.getBaseDataFolder().resolve(versionFolder);

        try {
            Files.createDirectories(folderPath);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to create data directory: " + folderPath, exception);
        }

        return folderPath;
    }

    private Optional<ResponseConsumer> getResponseConsumer() {
        return Optional.ofNullable(this.responseConsumer);
    }

    private void sendNewResponse(final String statusMessage) {
        this.getResponseConsumer().ifPresent(consumer -> consumer.onStatusUpdate(statusMessage));
    }

    private void downloadFile(final URL url, final Path path, final String fileType) throws IOException {
        try (final DurationTracker ignored = new DurationTracker(
                duration -> log.info("Successfully downloaded {} file in {}!", fileType, duration))) {
            log.info("Downloading {} file from Mojang...", fileType);
            final Request httpRequest = new Request.Builder().url(url).build();

            try (final Response response = this.httpClient.newCall(httpRequest).execute()) {
                if (response.body() == null) {
                    throw new IOException("Response body was null");
                }

                final long length = response.body().contentLength();
                if (Files.exists(path) && Files.size(path) == length) {
                    log.info("Already have {}, skipping download.", path.getFileName());
                    return;
                }

                FileUtil.remove(path);
                final Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (BufferedSink sink = Okio.buffer(Okio.sink(path))) {
                    sink.writeAll(response.body().source());
                }
            }
        }
    }

    private boolean isValid() {
        if (this.getJarUrl() == null) {
            log.error(
                    "Failed to find JAR URL for version {}-{}",
                    this.request.type(),
                    this.request.getVersion().id());
            this.sendNewResponse(String.format(
                    "Failed to find JAR URL for version %s-%s",
                    this.request.type(), this.request.getVersion().id()));
            return false;
        }

        return true;
    }

    private boolean validateOptions() {
        if (this.options.setupGradleProject() && !this.options.decompile()) {
            log.error("Gradle project setup requires decompile to be enabled.");
            this.sendNewResponse("Gradle setup requires decompile to be enabled.");
            return false;
        }

        if (this.options.setupGradleProject() && !this.options.downloadLibraries()) {
            log.error("Gradle project setup requires downloading libraries.");
            this.sendNewResponse("Gradle setup requires library downloads.");
            return false;
        }

        return true;
    }

    @Nullable private URL getJarUrl() {
        return this.request.getJar().orElse(null);
    }

    @Nullable private URL getMappingsUrl() {
        return this.request.getMappings().orElse(null);
    }

    private CompletableFuture<Void> downloadJar() {
        final URL jarUrl = Objects.requireNonNull(this.getJarUrl(), "jar URL should be validated before download");
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.downloadFile(jarUrl, this.jarPath, "JAR");
            } catch (final IOException e) {
                throw new CompletionException(e);
            }

            return null;
        });
    }

    private CompletableFuture<Void> downloadMappings() {
        final URL mappingsUrl = this.getMappingsUrl();
        if (mappingsUrl == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                this.downloadFile(mappingsUrl, this.mappingsPath, "mappings");
            } catch (final IOException exception) {
                throw new CompletionException(exception);
            }

            return null;
        });
    }

    private void remapJar() {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Remapping completed in {}!", duration))) {
            this.sendNewResponse("Remapping...");

            if (!Files.exists(this.remappedJar)) {
                log.info("Remapping {} file...", this.jarPath.getFileName());
                this.remapper.remap(this.jarPath, this.mappingsPath, this.remappedJar);
            } else {
                log.info("{} already remapped... skipping mapping.", this.remappedJar.getFileName());
            }
        }
    }

    private void decompileJar(final Path jarPath) throws IOException {
        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Decompiling completed in {}!", duration))) {
            log.info("Decompiling final JAR file.");
            this.sendNewResponse("Decompiling... This will take a while!");

            FileUtil.remove(this.decompiledJarPath);
            Files.createDirectories(this.decompiledJarPath);

            this.decompiler.decompile(jarPath, this.decompiledJarPath, this.resolveDecompilerLibraries());

            if (this.options.zipDecompileOutput()) {
                // Pack the decompiled files into a zip file
                log.info("Packing decompiled files into {}", this.decompiledZipPath);
                this.sendNewResponse("Packing decompiled files ...");
                FileUtil.remove(this.decompiledZipPath);
                FileUtil.zip(this.decompiledJarPath, this.decompiledZipPath);
            }
        }
    }

    private List<Path> resolveDecompilerLibraries() throws IOException {
        if (!this.options.downloadLibraries()) {
            return List.of();
        }
        if (!this.decompiler.supportsExternalLibraries()) {
            log.info(
                    "{} does not support external libraries; decompiling without downloaded dependencies.",
                    this.decompiler.getClass().getSimpleName());
            return List.of();
        }

        final List<Path> libraries = this.getDownloadedLibraryJars();
        if (!libraries.isEmpty()) {
            log.info(
                    "Passing {} downloaded libraries to {}.",
                    libraries.size(),
                    this.decompiler.getClass().getSimpleName());
        } else {
            log.warn(
                    "No downloaded library jars found to pass to {}.",
                    this.decompiler.getClass().getSimpleName());
        }
        return libraries;
    }

    private List<Path> getDownloadedLibraryJars() throws IOException {
        if (!Files.isDirectory(this.librariesPath)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(this.librariesPath)) {
            return files.filter(Files::isRegularFile)
                    .filter(this::isLibraryJar)
                    .sorted()
                    .toList();
        }
    }

    private boolean isLibraryJar(final Path path) {
        final String fileName = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
        return fileName.endsWith(".jar") && !fileName.contains("-natives-");
    }

    private void downloadLibraries() throws IOException {
        final List<LibraryArtifact> libraries = this.request.getLibraries().stream()
                .filter(library -> library.path() != null && library.url() != null)
                .toList();

        if (libraries.isEmpty()) {
            log.warn("No downloadable library artifacts were found for this version.");
            this.sendNewResponse("No libraries found to download.");
            return;
        }

        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Library download completed in {}!", duration))) {
            this.sendNewResponse("Downloading libraries...");
            for (int i = 0; i < libraries.size(); i++) {
                final LibraryArtifact library = libraries.get(i);
                final Path outputPath = this.librariesPath.resolve(library.path());
                this.downloadFile(library.url(), outputPath, "library");

                final int downloaded = i + 1;
                if (downloaded == libraries.size() || downloaded % 25 == 0) {
                    this.sendNewResponse(
                            String.format("Downloading libraries... (%d/%d)", downloaded, libraries.size()));
                }
            }
        }
    }

    private void setupGradleProject() throws IOException {
        if (!Files.isDirectory(this.decompiledJarPath)) {
            throw new IOException("Decompiled sources directory was not found: " + this.decompiledJarPath);
        }

        if (!Files.isDirectory(this.librariesPath)) {
            throw new IOException("Libraries directory was not found: " + this.librariesPath);
        }

        try (final DurationTracker ignored =
                new DurationTracker(duration -> log.info("Gradle project setup completed in {}!", duration))) {
            this.sendNewResponse("Setting up Gradle project...");
            FileUtil.remove(this.gradleProjectPath);
            Files.createDirectories(this.gradleProjectPath);
            this.writeGradleProjectFiles();
        }
    }

    private void writeGradleProjectFiles() throws IOException {
        final String projectName = String.format(
                "minecraft-%s-%s",
                this.request.type().name().toLowerCase(Locale.ENGLISH),
                this.request.getVersion().id());
        final String minecraftVersion = this.request.getVersion().id();
        final int javaVersion = this.request.getJavaVersion().orElse(DEFAULT_GRADLE_JAVA_VERSION);
        final Optional<String> mainClass = this.request.getMainClass();
        final String escapedMainClass = mainClass.map(this::escapeKotlinString).orElse("");
        final String escapedMinecraftVersion = this.escapeKotlinString(minecraftVersion);
        final String escapedProjectName = this.escapeKotlinString(projectName);

        final String settingsContent =
                String.format(Locale.ENGLISH, """
                    pluginManagement {
                        repositories {
                            gradlePluginPortal()
                            mavenCentral()
                        }
                    }

                    plugins {
                        id("org.gradle.toolchains.foojay-resolver-convention") version "%s"
                    }

                    rootProject.name = "%s"
                    """, FOOJAY_RESOLVER_PLUGIN_VERSION, escapedProjectName);
        final String buildContent = String.format(
                Locale.ENGLISH,
                """
                plugins {
                    java
                    %s
                }

                group = "com.shanebeestudios.generated"
                version = "%s"
                description = "Generated Minecraft base project (%s)"

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(%d)
                    }
                    sourceCompatibility = JavaVersion.toVersion(%d)
                    targetCompatibility = JavaVersion.toVersion(%d)
                }

                repositories {
                    mavenCentral()
                }

                sourceSets {
                    named("main") {
                        java.setSrcDirs(listOf("../decompiled"))
                    }
                }

                dependencies {
                    implementation(fileTree("../libraries") {
                        include("**/*.jar")
                        exclude("**/*-natives-*.jar")
                    })
                    runtimeOnly(fileTree("../libraries") {
                        include("**/*-natives-*.jar")
                    })
                }

                tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
                    options.encoding = "UTF-8"
                    options.release = %d
                }
                %s
                """,
                mainClass.isPresent() ? "application" : "",
                escapedMinecraftVersion,
                escapedProjectName,
                javaVersion,
                javaVersion,
                javaVersion,
                javaVersion,
                mainClass.isPresent() ? String.format(Locale.ENGLISH, """

                    application {
                        mainClass = "%s"
                    }

                    tasks.register<JavaExec>("runMinecraft") {
                        group = "application"
                        description = "Run Minecraft main class from decompiled sources"
                        mainClass.set(application.mainClass)
                        classpath = sourceSets["main"].runtimeClasspath
                        workingDir = projectDir
                    }
                    """, escapedMainClass) : "");
        final String gradlePropertiesContent = """
            org.gradle.jvmargs=-Xmx4G -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            org.gradle.caching=true
            org.gradle.java.installations.auto-download=true
            org.gradle.java.installations.auto-detect=true
            """;
        final String gitignoreContent = """
            .gradle/
            build/
            """;
        final String readmeContent = String.format(
                Locale.ENGLISH,
                """
                # Generated base project

                Minecraft version: `%s`
                Java version: `%d`
                Main class: `%s`

                This project compiles against:
                - Decompiled sources in `../decompiled`
                - Downloaded libraries in `../libraries`

                Run:
                `gradle build`
                %s
                """,
                minecraftVersion,
                javaVersion,
                mainClass.orElse("n/a"),
                mainClass.isPresent() ? "`gradle runMinecraft`" : "");

        Files.writeString(this.gradleProjectPath.resolve("settings.gradle.kts"), settingsContent);
        Files.writeString(this.gradleProjectPath.resolve("build.gradle.kts"), buildContent);
        Files.writeString(this.gradleProjectPath.resolve("gradle.properties"), gradlePropertiesContent);
        Files.writeString(this.gradleProjectPath.resolve(".gitignore"), gitignoreContent);
        Files.writeString(this.gradleProjectPath.resolve("README.md"), readmeContent);
    }

    private String escapeKotlinString(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean init() {
        if (!this.isValid() || !this.validateOptions()) {
            return false;
        }

        try (final DurationTracker ignored = new DurationTracker(duration -> {
            log.info("Completed in {}!", duration);
            this.sendNewResponse(String.format("Completed in %s!", duration));
        })) {
            // Download the JAR and mappings files
            if (this.getMappingsUrl() != null) {
                this.sendNewResponse("Downloading JAR & MAPPINGS...");
            } else {
                this.sendNewResponse("Downloading JAR...");
            }
            CompletableFuture.allOf(this.downloadJar(), this.downloadMappings()).join();
            if (this.options.downloadLibraries()) {
                this.downloadLibraries();
            }

            boolean remapped = false;
            if (this.options.remap()) {
                if (this.getMappingsUrl() != null) {
                    this.remapJar();
                    remapped = true;
                } else {
                    log.warn(
                            "Remapping requested but no mappings found for version {}",
                            this.request.getVersion().id());
                }
            }

            if (this.options.decompile()) {
                this.decompileJar(remapped ? this.remappedJar : this.jarPath);
            }

            if (this.options.setupGradleProject()) {
                this.setupGradleProject();
            }
            return true;
        } catch (final IOException e) {
            log.error("Failed to run Processor!", e);
            return false;
        }
    }

    public void cleanup() {
        if (this.remapper instanceof final Cleanup cleanup) {
            cleanup.cleanup();
        }

        if (this.decompiler instanceof final Cleanup cleanup) {
            cleanup.cleanup();
        }
    }
}
