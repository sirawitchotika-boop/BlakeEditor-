plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A personal release is signed only by an explicit, separate private build.
// Normal PR CI and unsigned release checks never load signing credentials.
val blakePersonalSign = providers.gradleProperty("blakePersonalSign").orNull == "true"
val blakeSigningInputs: Map<String, String?> = if (blakePersonalSign) {
    mapOf(
        "BLAKE_PERSONAL_KEYSTORE_PATH" to providers.environmentVariable("BLAKE_PERSONAL_KEYSTORE_PATH").orNull,
        "BLAKE_PERSONAL_STORE_PASSWORD" to providers.environmentVariable("BLAKE_PERSONAL_STORE_PASSWORD").orNull,
        "BLAKE_PERSONAL_KEY_ALIAS" to providers.environmentVariable("BLAKE_PERSONAL_KEY_ALIAS").orNull,
        "BLAKE_PERSONAL_KEY_PASSWORD" to providers.environmentVariable("BLAKE_PERSONAL_KEY_PASSWORD").orNull,
    )
} else {
    emptyMap()
}
if (blakePersonalSign) {
    val missing = blakeSigningInputs.filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) {
        "Personal release signing was requested but inputs are missing: ${missing.joinToString(", ")}"
    }
}

android {
    namespace = "com.blake.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blake.editor.personal"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (blakePersonalSign) {
            create("personalRelease") {
                storeFile = file(blakeSigningInputs.getValue("BLAKE_PERSONAL_KEYSTORE_PATH")!!)
                storePassword = blakeSigningInputs.getValue("BLAKE_PERSONAL_STORE_PASSWORD")
                keyAlias = blakeSigningInputs.getValue("BLAKE_PERSONAL_KEY_ALIAS")
                keyPassword = blakeSigningInputs.getValue("BLAKE_PERSONAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Do not enable signing for ordinary PR, debug, or unsigned builds.
            if (blakePersonalSign) {
                signingConfig = signingConfigs.getByName("personalRelease")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Optional side-by-side phone-test build so CI debug APKs never
            // collide with the installed personal release app.
            applicationIdSuffix =
                providers.gradleProperty("isolatedTestApkSuffix").orNull
                    ?: if (providers.gradleProperty("isolatedTestApk").orNull == "true") {
                        ".isolatedtest"
                    } else {
                        ".debug"
                    }
            isDebuggable = true

            val isolatedRunTag = providers.gradleProperty("isolatedTestApkSuffix").orNull
                ?.let { Regex("""^\.test([0-9]+)a([0-9]+)$""").matchEntire(it) }
                ?.let { "T${it.groupValues[1].takeLast(6)}a${it.groupValues[2]}" }
                ?: "Debug"
            resValue("string", "blake_debug_editor_label", "Blake $isolatedRunTag")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Pure-Java CommonMark + GFM extensions for instant native Markdown reading
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
