plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "io.github.dongyuzhao"
version = "0.1.0"

android {
    namespace = "io.github.dongyuzhao.mathrenderer"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // JavaScriptEngine: out-of-process V8 sandbox (requires Android 13 / API 33 at runtime;
    // the library handles lower API levels by returning an error).
    implementation("androidx.javascriptengine:javascriptengine:1.0.0-beta01")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.guava:guava:33.2.1-android")

    // Unit tests (JVM-local)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "io.github.dongyuzhao"
            artifactId = "math-renderer-android"
            version = "0.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
