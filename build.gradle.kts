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
version = "2.12.12"
// x-release-please-end
description = "McDeob"

application {
    mainClass = "com.shanebeestudios.mcdeop.McDeob"
}

javafx {
    version = libs.versions.javafx.get()
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
    implementation(libs.jadx.core)
    implementation(libs.jadx.java.input)
    implementation(libs.picocli)
    implementation(libs.slf4j.simple)
    implementation(libs.okhttp)
    // Used directly by the release checker; previously reachable only through the launchermeta shadow jar.
    implementation(libs.jackson.databind)

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
        // java-library rather than java, so subprojects can separate their api from their
        // implementation dependencies.
        plugin("java-library")
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

    dependencies {
        // Annotations are erased at build time, so compileOnly is enough. Declared explicitly because
        // the code uses @Nullable; it previously resolved only as a transitive of the decompiler
        // backends, where a dependency bump that dropped it would have broken compilation.
        "compileOnly"(rootProject.libs.jetbrains.annotations)

        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
        }
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
    }
}

/**
 * Resolved once per build: the lookup shells out to git, and it is read by two build config fields.
 *
 * Override with `-PgithubRepoName=owner/repo` (or a `gradle.properties` entry) when building outside
 * a git checkout, such as from a source tarball, where the lookup cannot succeed.
 */
val githubRepoName: String by lazy {
    val override = providers.gradleProperty("githubRepoName").orNull
    if (!override.isNullOrBlank()) {
        return@lazy override
    }

    val url =
        runCatching {
            val process =
                ProcessBuilder("git", "config", "--get", "remote.origin.url")
                    .redirectErrorStream(true)
                    .start()
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        }.getOrDefault("")

    val regex = Regex("""github\.com[/:](.+?)(?:\.git)?$""")
    regex.find(url)?.groupValues?.get(1) ?: run {
        logger.warn(
            "Could not determine the GitHub repository from git remote 'origin'. " +
                "The in-app update check and repository link will be inactive. " +
                "Set -PgithubRepoName=owner/repo to override.",
        )
        "unknown/unknown"
    }
}

buildConfig {
    className("GeneratedConstant")
    packageName("com.shanebeestudios.mcdeop.util")

    useJavaOutput()

    buildConfigField("VERSION", provider { version.toString() })
    buildConfigField(
        "FOOJAY_RESOLVER_VERSION",
        provider {
            libs.plugins.foojay.resolver
                .get()
                .version
                .toString()
        },
    )
    buildConfigField(
        "JETBRAINS_ANNOTATIONS_VERSION",
        provider { libs.versions.jetbrainsAnnotations.get() },
    )
    buildConfigField(
        "JSR305_VERSION",
        provider { libs.versions.jsr305.get() },
    )
    buildConfigField(
        "JSPECIFY_VERSION",
        provider { libs.versions.jspecify.get() },
    )
    buildConfigField("GITHUB_REPO_NAME", provider { githubRepoName })
    buildConfigField("GITHUB_REPO_URL", provider { "https://github.com/$githubRepoName" })
}