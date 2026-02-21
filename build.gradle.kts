import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    java
    application
    alias(libs.plugins.lombok)
    alias(libs.plugins.spotless)
    alias(libs.plugins.shadow)
    alias(libs.plugins.sentry)
    alias(libs.plugins.javafxplugin)
    alias(libs.plugins.buildconfig)
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.graphics")
}

group = "com.shanebeestudios"
// x-release-please-start-version
version = "2.9.3"
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
            targetExclude(layout.buildDirectory.asFileTree.matching { include("generated/**/*.java") })

            importOrder()
            removeUnusedImports()

            cleanthat()

            palantirJavaFormat()

            formatAnnotations()
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

buildConfig {
    className("GeneratedConstant")
    packageName("com.shanebeestudios.mcdeop.util")

    useJavaOutput()

    buildConfigField("VERSION", provider { version.toString() })
}