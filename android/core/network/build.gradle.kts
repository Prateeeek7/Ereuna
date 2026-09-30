plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.researchradar.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // Backend address. Override for a physical device on your LAN with
        //   ./gradlew assembleDebug -PresearchRadarApiUrl=http://192.168.1.20:8000/
        // or by adding researchRadarApiUrl=... to ~/.gradle/gradle.properties.
        // Default 10.0.2.2 is the host machine as seen from the Android emulator.
        val apiUrl = (project.findProperty("researchRadarApiUrl") as String?)
            ?.trim()?.let { if (it.endsWith("/")) it else "$it/" }
            ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
}
