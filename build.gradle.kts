import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

plugins {
    `java-library`
    application
    alias(libs.plugins.lombok)
    alias(libs.plugins.spotless)
    alias(libs.plugins.shadow)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.javafxplugin)
    alias(libs.plugins.gluonfx)
}

group = "com.shanebeestudios"
// x-release-please-start-version
version = "2.10.0"
// x-release-please-end
description = "McDeob"

application {
    mainClass = "com.shanebeestudios.mcdeop.McDeob"
}

javafx {
    version = "21.0.4"
    modules = listOf("javafx.controls", "javafx.graphics")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common", "shadow"))
    implementation(project(":launchermeta", "shadow"))
    implementation(libs.reconstruct.common)
    implementation(libs.vineflower)
    implementation(libs.picocli)
    implementation(libs.slf4j.simple)
    implementation(libs.okhttp)

    annotationProcessor(libs.picocli.codegen)
}

val uiIconSource = layout.projectDirectory.file("src/main/resources/images/1024.png")
val windowsIconOutput = layout.projectDirectory.file("src/windows/assets/icon.ico")
val macIconsetOutputDir = layout.projectDirectory.dir("src/macos/assets/AppIcon.iconset")

val prepareNativeIcons by tasks.registering {
    group = "build"
    description = "Generate native icon assets from the UI icon."

    inputs.file(uiIconSource)
    outputs.file(windowsIconOutput)
    outputs.dir(macIconsetOutputDir)

    doLast {
        fun writeBytesIfChanged(
            target: java.io.File,
            bytes: ByteArray,
        ) {
            val current = if (target.exists()) target.readBytes() else null
            if (current == null || !current.contentEquals(bytes)) {
                target.parentFile.mkdirs()
                target.writeBytes(bytes)
            }
        }

        fun scale(
            source: BufferedImage,
            size: Int,
        ): BufferedImage {
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, size, size, null)
            graphics.dispose()
            return image
        }

        val sourceFile = uiIconSource.asFile
        if (!sourceFile.exists()) {
            throw GradleException("UI icon not found: ${sourceFile.path}")
        }
        val source = ImageIO.read(sourceFile) ?: throw GradleException("Could not read ${sourceFile.path}")

        val macIcons =
            listOf(
                "icon_16@1x.png" to 16,
                "icon_16@2x.png" to 32,
                "icon_32@1x.png" to 32,
                "icon_32@2x.png" to 64,
                "icon_128@1x.png" to 128,
                "icon_128@2x.png" to 256,
                "icon_256@1x.png" to 256,
                "icon_256@2x.png" to 512,
                "icon_512@1x.png" to 512,
                "icon_512@2x.png" to 1024,
            )

        val iconsetDir = macIconsetOutputDir.asFile
        iconsetDir.mkdirs()
        macIcons.forEach { (name, size) ->
            val bytes =
                ByteArrayOutputStream().use { output ->
                    ImageIO.write(scale(source, size), "png", output)
                    output.toByteArray()
                }
            writeBytesIfChanged(iconsetDir.resolve(name), bytes)
        }

        val windowsPng =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(scale(source, 256), "png", output)
                output.toByteArray()
            }
        val iconDirSize = 6 + 16
        val header =
            ByteBuffer
                .allocate(iconDirSize)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putShort(0) // reserved
                    putShort(1) // image type: icon
                    putShort(1) // image count
                    put(0) // width = 256
                    put(0) // height = 256
                    put(0) // color count
                    put(0) // reserved
                    putShort(1) // color planes
                    putShort(32) // bits per pixel
                    putInt(windowsPng.size)
                    putInt(iconDirSize)
                }.array()

        writeBytesIfChanged(windowsIconOutput.asFile, header + windowsPng)
    }
}

tasks
    .matching { it.name in setOf("nativeBuild", "nativePackage", "nativeRun", "nativeCompile", "nativeLink") }
    .configureEach {
        dependsOn(prepareNativeIcons)
    }

tasks {
    named<JavaCompile>("compileJava") {
        options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    }

    shadowJar {
        manifest.attributes["Implementation-Version"] = project.version
    }
}

gluonfx {
    target = "host"
    compilerArgs =
        listOf(
            "--enable-url-protocols=https",
            "-H:+ReportExceptionStackTraces",
        )
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
            languageVersion = JavaLanguageVersion.of(21)
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