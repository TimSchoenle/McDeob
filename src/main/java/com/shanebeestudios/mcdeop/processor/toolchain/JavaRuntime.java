package com.shanebeestudios.mcdeop.processor.toolchain;

import java.nio.file.Path;

/**
 * A Java launcher McDeob can hand work to.
 *
 * @param executable path to the {@code java} launcher
 * @param featureVersion the feature release number, for example {@code 21}
 */
public record JavaRuntime(Path executable, int featureVersion) {}
