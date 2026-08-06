# McDeob

[![Latest Release](https://img.shields.io/github/v/release/TimSchoenle/McDeob?include_prereleases)](https://github.com/TimSchoenle/McDeob/releases/latest)
[![License](https://img.shields.io/github/license/TimSchoenle/McDeob)](https://github.com/TimSchoenle/McDeob/blob/master/LICENSE)

McDeob is a desktop tool that helps you generate readable Minecraft client/server source output from official game
files.

## Download and Use

1. Open the latest release page:  
   [https://github.com/TimSchoenle/McDeob/releases/latest](https://github.com/TimSchoenle/McDeob/releases/latest)
2. Download the file that matches your system.
3. Run the app.
4. In the UI:
    - Choose `Client` or `Server`
    - Pick a Minecraft version
    - Enable the options you want (`Remap`, `Decompile`, `Zip`, `Repair Sources`)
    - Click start and wait for completion
5. Open the output folder shown by the app.

## What You Get

- A remapped jar and/or decompiled source output
- Optional zip archive (if enabled)
- Files organized by version and selected options

## Screenshot

![McDeob UI](docs/images/ui.png)

## Before You Start

- Internet connection is required (the app downloads Minecraft files and mappings).
- If you run a `.jar` build, install Java 21 or newer.

## Important Legal Notice

- Output is for personal use only.
- Decompiled Minecraft output contains proprietary code.
- Do not upload or redistribute generated Minecraft source files.

## Developer Section

This section is for contributors and power users who want to run McDeob from source.

### Requirements

- Java 21+
- GraalVM 21+ (`GRAALVM_HOME` set) for native builds
- Native toolchain:
  - Windows: Visual Studio Build Tools (C++ workload)
  - macOS: Xcode Command Line Tools
  - Linux: `gcc`, `g++`, and build essentials

### Run in GUI Mode

```bash
./gradlew run
```

### CLI Examples

```bash
./gradlew run --args="--versions"
./gradlew run --args="--type client --version 1.21.4 --remap --decompile --zip"
./gradlew run --args="--type client --version 1.21.4 --decompile --decompiler fernflower"
./gradlew run --args="--type client --version 1.21.4 --decompile --decompiler jadx"
./gradlew run --args="--type client --version 1.21.4 --libraries --gradle-project"
```

- `--gradle-project` requires `--decompile` and `--libraries`.
- `--decompiler` supports `vineflower` (default), `fernflower`, `cfr`, and `jadx`.

### Source Repair

Decompilers describe what a class file does, not the source it was compiled from. Wherever the
compiler erased something the two differ, and the output stops being a legal Java program: casts
between two parameterisations of a type leave no trace in the bytecode and are dropped, variables
that lived in separate scopes collide once those scopes are merged, and desugared record patterns
leave temporaries with no declaration.

`--repair-sources` type-checks the decompiled tree and repairs what the compiler rejects, one class
of defect at a time, until it compiles or nothing more is within reach. It is on by default with
`--gradle-project`, which is the mode whose output is meant to build, and can be turned off with
`--no-repair-sources`.

```bash
./gradlew run --args="--type client --version 1.21.4 --decompile --libraries --repair-sources"
```

- Requires `--decompile` and `--libraries`; type-checking needs both the sources and what they
  compile against.
- Requires a JDK at least as new as the release the sources target. Running on a JDK it is used
  directly; a native image has no compiler of its own and borrows one from `JAVA_HOME`, the `PATH`,
  or a Gradle toolchain under `~/.gradle/jdks`.
- Errors that no repair covers are logged and left in place, so a version that hits an unfamiliar
  defect still produces output.
- Note that a native image also decompiles without the JDK type hierarchy, because the `jrt`
  filesystem it needs is not available there. That yields noticeably more defects to begin with than
  a `.jar` run does, so a `.jar` run gives the best chance of a project that builds.

The generated project also declares the annotation libraries Mojang compiles against but does not
publish as runtime libraries, such as `org.jetbrains:annotations` and `com.google.code.findbugs:jsr305`.
They are detected from the decompiled imports and resolved from Maven Central, pinned to the version
that was current when the Minecraft version released. Imports that cannot be resolved are logged and
listed in the generated `README.md`.

### Native Build (GluonFX)

```bash
./gradlew nativeBuild
./gradlew nativeRun
./gradlew nativePackage
```

Native artifacts are generated in `build/gluonfx/`.

### Processing Time Notes

- Remapping usually takes around 2 minutes, with visible progress.
- Decompiling usually takes around 3 minutes, and may not show fine-grained progress.
- Times vary by machine and selected options.

### Core Processing Tools

1. [Reconstruct](https://github.com/LXGaming/Reconstruct) for remapping Minecraft jars.
2. [Vineflower](https://github.com/Vineflower/vineflower) for decompilation.
3. [Fernflower profile](https://github.com/JetBrains/intellij-community/tree/master/plugins/java-decompiler/engine) for legacy-style output via Vineflower engine support.
4. [JADX](https://github.com/skylot/jadx) as an additional decompiler backend.
