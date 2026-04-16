plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.opensmarthome.speaker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.opensmarthome.speaker"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // externalNativeBuild (llama.cpp) disabled — using MediaPipe
        // externalNativeBuild {
        //     cmake {
        //         arguments += listOf(
        //             "-DANDROID_ARM_NEON=TRUE",
        //             "-DCMAKE_BUILD_TYPE=Release",
        //             "-DCMAKE_C_FLAGS=-O3 -march=armv8.2-a+fp16+dotprod -DNDEBUG",
        //             "-DCMAKE_CXX_FLAGS=-O3 -march=armv8.2-a+fp16+dotprod -DNDEBUG"
        //         )
        //     }
        // }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        // Baseline captures existing warnings so only new issues fail CI.
        // Regenerate with `./gradlew updateLintBaseline`.
        baseline = file("lint-baseline.xml")
        checkDependencies = false
        warningsAsErrors = false
        abortOnError = true
    }

    // llama.cpp JNI disabled — using MediaPipe LLM Inference (GPU accelerated)
    // To re-enable llama.cpp, uncomment below and install NDK 27.0.12077973
    // val ndkDir = file("${android.sdkDirectory}/ndk/27.0.12077973")
    // if (ndkDir.exists()) {
    //     externalNativeBuild {
    //         cmake {
    //             path = file("src/main/cpp/CMakeLists.txt")
    //             version = "3.22.1"
    //         }
    //     }
    //     ndkVersion = "27.0.12077973"
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests {
            // Required for JaCoCo to instrument tests.
            isIncludeAndroidResources = true
        }
    }
}

// JaCoCo coverage report. Run:
//   ./gradlew testDebugUnitTest jacocoTestReport
// Output: app/build/reports/jacoco/jacocoTestReport/html/index.html
// Aim: 80%+ on non-UI code.
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates code coverage report for debug unit tests."
    dependsOn("testDebugUnitTest")

    reports {
        html.required.set(true)
        xml.required.set(true)
    }

    val fileFilter = listOf(
        // UI layer — not worth mocking Compose for coverage, covered via manual QA
        "**/ui/**",
        // Generated code (Hilt / Moshi / Room / Compose)
        "**/R.class", "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*_Factory*.*",
        "**/*_HiltModules*.*",
        "**/*_Impl*.*",
        "**/hilt_aggregated_deps/**",
        "**/*JsonAdapter*.*",
        "**/*ComposableSingletons*.*",
        "**/*LambdaImpl*.*"
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    val mainSrc = "${projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory.get()) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/*.exec",
            "jacoco/testDebugUnitTest.exec"
        )
    })
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Android Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.security.crypto)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)

    // MQTT
    implementation(libs.paho.mqtt)

    // On-device LLM (LiteRT-LM for Gemma 4 + MediaPipe for Gemma 3)
    implementation(libs.mediapipe.genai)
    implementation(libs.litertlm.android)

    // Wake word (Vosk offline speech recognition)
    implementation(libs.vosk)

    // Logging
    implementation(libs.timber)

    // Unit Tests
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)

    // Instrumented Tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
