import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    java
    application
    id("io.freefair.lombok") version "9.2.0"
    id("com.diffplug.spotless") version "8.2.1"
    id("com.gradleup.shadow") version "9.3.1"
    id("io.sentry.jvm.gradle") version "6.0.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.graphics")
}

group = "com.shanebeestudios"
// x-release-please-start-version
version = "2.8.0"
// x-release-please-end
description = "McDeob"

application {
    mainClass = "com.shanebeestudios.mcdeop.McDeob"
}

repositories {
    mavenCentral()

    maven("https://jitpack.io")
    maven("https://repo.kenzie.mx/releases")
    maven("https://repo.maven.apache.org/maven2/")
}

dependencies {
    implementation(project(":common", "shadow"))
    implementation(project(":launchermeta", "shadow"))
    implementation(libs.mirror)
    implementation(libs.reconstruct.common)
    implementation(libs.vineflower)
    implementation(libs.picocli)
    implementation(libs.slf4j.simple)
    implementation(libs.okhttp)

    implementation(libs.dagger)
    annotationProcessor(libs.dagger.compiler)
    annotationProcessor(libs.picocli.codegen)
}

tasks {
    shadowJar {
        manifest.attributes["Implementation-Version"] = project.version
    }
}

allprojects {
    apply {
        plugin("java")
        plugin("com.diffplug.spotless")
        plugin("io.freefair.lombok")
        plugin("com.gradleup.shadow")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
    }

    spotless {
        java {
            // Use the default importOrder configuration
            importOrder()
            removeUnusedImports()

            // Cleanthat will refactor your code, but it may break your style: apply it before your formatter
            cleanthat()

            palantirJavaFormat()

            formatAnnotations() // fixes formatting of type annotations
        }

        kotlinGradle {
            ktlint()
        }

        yaml {
            target("*.yaml")
            jackson()
        }
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
        }

        withType<Javadoc> {
            options.encoding = "UTF-8"
        }

        withType<ShadowJar> {
            // https://github.com/johnrengelman/shadow/issues/857
            // archiveClassifier.set("")

            // dependsOn("distTar", "distZip")
        }
    }
}