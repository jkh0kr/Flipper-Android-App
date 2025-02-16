plugins {
    id("flipper.android-compose")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
}

android.namespace = "com.flipperdevices.archive.api"

dependencies {
    implementation(projects.components.bridge.dao.api)
    implementation(projects.components.bridge.synchronization.api)
    implementation(projects.components.core.ui.navigation)
    implementation(projects.components.core.ui.decompose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.decompose)

    implementation(libs.kotlin.serialization.json)
}
