plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.android.compose.screenshot") version "0.0.1-alpha16"
}

ktlint {
    version.set("1.8.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencyLocking {
    lockAllConfigurations()
}

val releaseVersion = providers
    .gradleProperty("releaseVersion")
    .orElse("0.1.0")
    .get()
    .removePrefix("v")
require(Regex("\\d{1,4}\\.\\d{1,3}(?:\\.\\d{1,3})?").matches(releaseVersion)) {
    "releaseVersion должен иметь вид 1.0 или 1.0.1"
}
val releaseParts = releaseVersion.split('.').map(String::toInt)
val releaseCode = releaseParts[0] * 1_000_000 + releaseParts[1] * 1_000 + releaseParts.getOrElse(2) { 0 }
require(releaseParts[0] <= 2099 && releaseCode in 1..2_100_000_000) { "Недопустимый номер версии" }

val signingNames = listOf(
    "SKACHAY_KEYSTORE_PATH",
    "SKACHAY_KEYSTORE_PASSWORD",
    "SKACHAY_KEY_ALIAS",
    "SKACHAY_KEY_PASSWORD",
)
val signingValues = signingNames.map { providers.environmentVariable(it).orNull }
require(signingValues.all { it.isNullOrBlank() } || signingValues.none { it.isNullOrBlank() }) {
    "Для подписывания нужны путь к ключу, оба пароля и псевдоним ключа"
}

android {
    namespace = "ru.skachay.app"
    compileSdk = 37
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "ru.skachay.app"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseCode
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (signingValues.none { it.isNullOrBlank() }) {
            create("production") {
                storeFile = file(requireNotNull(signingValues[0]))
                storePassword = requireNotNull(signingValues[1])
                keyAlias = requireNotNull(signingValues[2])
                keyPassword = requireNotNull(signingValues[3])
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("production")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(rootProject.layout.projectDirectory.dir("recipe-defaults"))

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.re2j:re2j:1.8")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
