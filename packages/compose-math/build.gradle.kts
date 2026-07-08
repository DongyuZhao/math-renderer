plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

if (!tasks.names.contains("prepareKotlinBuildScriptModel")) {
    tasks.register("prepareKotlinBuildScriptModel") {
        group = "help"
        description = "Compatibility task for IDE Kotlin build script model sync."
    }
}

plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
        jvmToolchain(libs.versions.jdk.get().toInt())
    }
}

extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

android {
    namespace = "io.github.dongyuzhao.composemath"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Roborazzi renders Compose on the JVM via Robolectric; needs Android
    // resources on the unit-test classpath.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    implementation(libs.quickjs.kt)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.unit)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    // Pixel snapshots (rasterization) via Roborazzi + Robolectric.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
