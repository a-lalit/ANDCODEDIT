plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.andcodedit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.andcodedit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Multiple bundled engines ship duplicate license/metadata files.
            excludes += "/META-INF/*.kotlin_module"
            pickFirsts += "META-INF/LICENSE*"
            pickFirsts += "META-INF/NOTICE*"
            pickFirsts += "**/*.properties"
        }
    }
    lint {
        // A working APK shouldn't be blocked by lint warnings/errors in CI.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines (Flow-based code execution / streaming terminal output)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WebView host for the Monaco editor
    implementation("androidx.webkit:webkit:1.12.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Data
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // DEX Mode — smali/dexlib2 (org.smali 2.5.2 is the latest on Maven Central;
    // package namespace is org.jf.dexlib2 / org.jf.* )
    implementation("org.smali:dexlib2:2.5.2")
    implementation("org.smali:smali:2.5.2")
    implementation("org.smali:baksmali:2.5.2")

    // Editor Engine — Sora Editor for professional code editing
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.5")
    implementation("io.github.Rosemoe.sora-editor:language-java:0.23.5")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.5")

    // Git
    // Using JGit for now, will add libgit2 via UniFFI later
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    // Terminal (PTY will be in rust-terminal module)
    // For now, we use a placeholder. Real implementation uses Rust UniFFI.

    // In-process language engines — these run INSIDE the app (bundled in the
    // APK, no external toolchain needed). Limited to PURE INTERPRETERS that emit
    // no JVM bytecode, because Android/ART can only load DEX, not runtime .class
    // files. That rules out Jython, JRuby and Groovy (all generate bytecode);
    // those languages run via the Termux toolchain instead.
    implementation("org.mozilla:rhino:1.7.15")              // JavaScript (interpreted, optimizationLevel=-1)
    implementation("org.luaj:luaj-jse:3.0.1")               // Lua 5.2 (pure interpreter)
    implementation("org.apache-extras.beanshell:bsh:2.0b6") // Java-like (AST interpreter)

    // Cloud / Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}