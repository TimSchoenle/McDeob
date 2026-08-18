package com.shanebeestudios.mcdeop.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GithubReleaseCheckerTest {

    @ParameterizedTest(name = "{1} is newer than {0}")
    @CsvSource({
        "2.12.10, 2.12.11",
        "2.12.10, 2.13.0",
        "2.12.10, 3.0.0",
        "2.12.9, 2.12.10",
        "2.12.10, v2.12.11",
        "v2.12.10, 2.12.11",
        "2.12, 2.12.1",
    })
    @DisplayName("reports an update when the latest version is greater")
    void detectsNewerVersions(final String current, final String latest) {
        assertTrue(GithubReleaseChecker.isNewer(current, latest));
    }

    @ParameterizedTest(name = "{1} is not newer than {0}")
    @CsvSource({
        "2.12.10, 2.12.10",
        "2.12.10, 2.12.9",
        "2.12.10, 2.11.99",
        "2.12.10, 1.99.99",
        "2.12.10, v2.12.10",
        "2.12.1, 2.12",
    })
    @DisplayName("reports no update when the latest version is equal or older")
    void ignoresOlderOrEqualVersions(final String current, final String latest) {
        assertFalse(GithubReleaseChecker.isNewer(current, latest));
    }

    @Test
    @DisplayName("treats a missing trailing part as zero rather than as newer")
    void treatsMissingPartsAsZero() {
        assertFalse(GithubReleaseChecker.isNewer("2.12.0", "2.12"));
        assertFalse(GithubReleaseChecker.isNewer("2.12", "2.12.0"));
    }

    @Test
    @DisplayName("compares numerically, not lexicographically")
    void comparesNumerically() {
        // A lexicographic comparison would rank "9" above "10" here.
        assertTrue(GithubReleaseChecker.isNewer("2.9.0", "2.10.0"));
        assertFalse(GithubReleaseChecker.isNewer("2.10.0", "2.9.0"));
    }

    @Test
    @DisplayName("does not claim an update when a version carries no digits")
    void handlesUnparseableVersions() {
        assertFalse(GithubReleaseChecker.isNewer("", "2.12.11"));
        assertFalse(GithubReleaseChecker.isNewer("2.12.10", ""));
        assertFalse(GithubReleaseChecker.isNewer("nightly", "2.12.11"));
    }
}
