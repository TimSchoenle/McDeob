package com.shanebeestudios.mcdeop.processor;

import com.shanebeestudios.mcdeop.util.FileUtil;
import com.shanebeestudios.mcdeop.util.GeneratedConstant;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GradleProjectWriter {
    private static final int DEFAULT_GRADLE_JAVA_VERSION = 21;
    private static final String FOOJAY_RESOLVER_PLUGIN_VERSION = "1.0.0";

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

        Run:
        `gradle build`
        {{#application}}`gradle runMinecraft`{{/application}}
        """;

    private final ResourceRequest request;
    private final ProcessorPaths paths;
    private final SimpleTemplateEngine templateEngine;

    GradleProjectWriter(final ResourceRequest request, final ProcessorPaths paths) {
        this.request = request;
        this.paths = paths;
        this.templateEngine = new SimpleTemplateEngine();
    }

    void setupGradleProject() throws IOException {
        if (!Files.isDirectory(this.paths.decompiledJarPath())) {
            throw new IOException("Decompiled sources directory was not found: " + this.paths.decompiledJarPath());
        }

        if (!Files.isDirectory(this.paths.librariesPath())) {
            throw new IOException("Libraries directory was not found: " + this.paths.librariesPath());
        }

        FileUtil.remove(this.paths.gradleProjectPath());
        Files.createDirectories(this.paths.gradleProjectPath());
        this.writeGradleProjectFiles();
    }

    private void writeGradleProjectFiles() throws IOException {
        final String projectName = String.format(
                "minecraft-%s-%s",
                this.request.type().name().toLowerCase(Locale.ENGLISH),
                this.request.getVersion().id());
        final String minecraftVersion = this.request.getVersion().id();
        final int javaVersion = this.request.getJavaVersion().orElse(DEFAULT_GRADLE_JAVA_VERSION);
        final Optional<String> mainClass = this.request.getMainClass();
        final boolean hasMainClass = mainClass.isPresent();

        final Map<String, String> values = Map.of(
                "foojayVersion", GeneratedConstant.FOOJAY_RESOLVER_VERSION,
                "projectName", this.escapeKotlinString(projectName),
                "minecraftVersion", this.escapeKotlinString(minecraftVersion),
                "minecraftVersionRaw", minecraftVersion,
                "javaVersion", Integer.toString(javaVersion),
                "mainClass", mainClass.map(this::escapeKotlinString).orElse(""),
                "mainClassRaw", mainClass.orElse("n/a"));
        final Set<String> sections = hasMainClass ? Set.of("application") : Set.of();

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

    private String escapeKotlinString(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
