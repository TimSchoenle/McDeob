dependencies {
    // Exposed on the public API: LauncherMeta's constructor takes an OkHttpClient.
    api(libs.okhttp)

    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}