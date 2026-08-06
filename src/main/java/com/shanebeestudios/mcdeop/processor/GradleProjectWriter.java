package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.processor.CompileDependencyResolver.CompileDependencies;
import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GradleProjectWriter {
    private static final int DEFAULT_GRADLE_JAVA_VERSION = 21;

    private static final String SETTINGS_TEMPLATE = """
        pluginManagement {
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
        }

        plugins {
            id("org.gradle.toolchains.foojay-resolver-convention") version "{{foojayVersion}}"
        }

        rootProject.name = "{{projectName}}"
        """;

    // The compile-only dependencies are rendered with their own indentation and trailing newline,
    // so the placeholder sits inline to keep the block tidy when there is nothing to add.
    private static final String BUILD_TEMPLATE = """
        plugins {
            java
            {{#application}}application{{/application}}
        }

        group = "com.shanebeestudios.generated"
        version = "{{minecraftVersion}}"
        description = "Generated Minecraft base project ({{projectName}})"

        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of({{javaVersion}})
            }
            sourceCompatibility = JavaVersion.toVersion({{javaVersion}})
            targetCompatibility = JavaVersion.toVersion({{javaVersion}})
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
        {{compileOnlyDependencies}}    implementation(fileTree("../libraries") {
                include("**/*.jar")
                exclude("**/*-natives-*.jar")
            })
            runtimeOnly(fileTree("../libraries") {
                include("**/*-natives-*.jar")
            })
        }

        tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = {{javaVersion}}
        }

        {{#application}}
        application {
            mainClass = "{{mainClass}}"
        }

        tasks.register<JavaExec>("runMinecraft") {
            group = "application"
            description = "Run Minecraft main class from decompiled sources"
            mainClass.set(application.mainClass)
            classpath = sourceSets["main"].runtimeClasspath
            workingDir = projectDir
        }
        {{/application}}
        """;

    private static final String GRADLE_PROPERTIES_TEMPLATE = """
        org.gradle.jvmargs=-Xmx4G -Dfile.encoding=UTF-8
        org.gradle.parallel=true
        org.gradle.caching=true
        org.gradle.java.installations.auto-download=true
        org.gradle.java.installations.auto-detect=true
        """;

    private static final String GITIGNORE_TEMPLATE = """
        .gradle/
        build/
        """;

    private static final String README_TEMPLATE = """
        # Generated base project

        Minecraft version: `{{minecraftVersionRaw}}`
        Java version: `{{javaVersion}}`
        Main class: `{{mainClassRaw}}`

        This project compiles against:
        - Decompiled sources in `../decompiled`
        - Downloaded libraries in `../libraries`
        - Annotation libraries resolved from Maven Central, which Mojang compiles against but does
          not ship as runtime libraries

        Run:
        `gradle build`
        {{#application}}`gradle runMinecraft`{{/application}}
        {{#unresolved}}

        ## Unresolved imports

        The following packages are imported by the decompiled sources but are provided neither by
        `../libraries` nor by any dependency McDeob knows about. Add the matching dependencies to
        `build.gradle.kts` manually:

        {{unresolvedPackages}}
        {{/unresolved}}
        """;

    private final ResourceRequest request;
    private final ProcessorPaths paths;
    private final SimpleTemplateEngine templateEngine;
    private final CompileDependencyResolver dependencyResolver;

    GradleProjectWriter(final ResourceRequest request, final ProcessorPaths paths) {
        this.request = request;
        this.paths = paths;
        this.templateEngine = new SimpleTemplateEngine();
        this.dependencyResolver = new CompileDependencyResolver();
    }

    void setupGradleProject() throws IOException {
        if (!Files.isDirectory(this.paths.decompiledJarPath())) {
            throw new IOException("Decompiled sources directory was not found: " + this.paths.decompiledJarPath());
        }

        if (!Files.isDirectory(this.paths.librariesPath())) {
            throw new IOException("Libraries directory was not found: " + this.paths.librariesPath());
        }

        final CompileDependencies dependencies = this.dependencyResolver.resolve(
                this.paths.decompiledJarPath(),
                this.paths.librariesPath(),
                this.request.getVersion().releaseTime().toLocalDate());

        FileUtil.remove(this.paths.gradleProjectPath());
        Files.createDirectories(this.paths.gradleProjectPath());
        this.writeGradleProjectFiles(dependencies);
    }

    private void writeGradleProjectFiles(final CompileDependencies dependencies) throws IOException {
        final String projectName = String.format(
                "minecraft-%s-%s",
                this.request.type().name().toLowerCase(Locale.ENGLISH),
                this.request.getVersion().id());
        final String minecraftVersion = this.request.getVersion().id();
        final int javaVersion = this.request.getJavaVersion().orElse(DEFAULT_GRADLE_JAVA_VERSION);
        final Optional<String> mainClass = this.request.getMainClass();

        final Map<String, String> values = Map.ofEntries(
                Map.entry("foojayVersion", GeneratedConstant.FOOJAY_RESOLVER_VERSION),
                Map.entry("projectName", this.escapeKotlinString(projectName)),
                Map.entry("minecraftVersion", this.escapeKotlinString(minecraftVersion)),
                Map.entry("minecraftVersionRaw", minecraftVersion),
                Map.entry("javaVersion", Integer.toString(javaVersion)),
                Map.entry("mainClass", mainClass.map(this::escapeKotlinString).orElse("")),
                Map.entry("mainClassRaw", mainClass.orElse("n/a")),
                Map.entry("compileOnlyDependencies", this.renderCompileOnlyDependencies(dependencies.coordinates())),
                Map.entry("unresolvedPackages", this.renderUnresolvedPackages(dependencies.unresolvedPackages())));

        final Set<String> sections = new HashSet<>();
        if (mainClass.isPresent()) {
            sections.add("application");
        }
        if (!dependencies.unresolvedPackages().isEmpty()) {
            sections.add("unresolved");
        }

        Files.writeString(
                this.paths.gradleProjectPath().resolve("settings.gradle.kts"),
                this.templateEngine.render(SETTINGS_TEMPLATE, values, Set.of()));
        Files.writeString(
                this.paths.gradleProjectPath().resolve("build.gradle.kts"),
                this.templateEngine.render(BUILD_TEMPLATE, values, sections));
        Files.writeString(this.paths.gradleProjectPath().resolve("gradle.properties"), GRADLE_PROPERTIES_TEMPLATE);
        Files.writeString(this.paths.gradleProjectPath().resolve(".gitignore"), GITIGNORE_TEMPLATE);
        Files.writeString(
                this.paths.gradleProjectPath().resolve("README.md"),
                this.templateEngine.render(README_TEMPLATE, values, sections));
    }

    /**
     * Renders the {@code compileOnly} declarations for the resolved annotation libraries.
     *
     * @param coordinates Maven coordinates to declare
     * @return an indented block ending in a blank line, or an empty string when nothing is needed
     */
    private String renderCompileOnlyDependencies(final List<String> coordinates) {
        if (coordinates.isEmpty()) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("    // Mojang compiles against these annotations, so they appear in the decompiled\n")
                .append("    // sources, but they are not shipped as runtime libraries and are therefore\n")
                .append("    // missing from ../libraries. Versions match this Minecraft release's date.\n");
        for (final String coordinate : coordinates) {
            builder.append("    compileOnly(\"").append(coordinate).append("\")\n");
        }

        return builder.append('\n').toString();
    }

    /**
     * Renders the unresolved packages as a Markdown list for the generated README.
     *
     * @param packages packages without a known source
     * @return a Markdown list, or an empty string when everything resolved
     */
    private String renderUnresolvedPackages(final List<String> packages) {
        if (packages.isEmpty()) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        for (final String packageName : packages) {
            builder.append("- `").append(packageName).append("`\n");
        }

        return builder.toString().stripTrailing();
    }

    private String escapeKotlinString(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
