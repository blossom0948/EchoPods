import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest

val appVersionName = "0.6.5.9"
val expectedDistributionCertificateSha256 =
    "BBE6103B BD9DDF37 7ED8677F 1150024B 42FB2932 3BC3AC38 C660F145 D73EB5A9".replace(" ", "")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutLibraries)
//    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

val localPropsFile = rootProject.file("local.properties")
val props = Properties().apply {
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
}

val releaseSigningKeys = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
)
val releaseSigningValues = releaseSigningKeys.associateWith { key ->
    (System.getenv("ECHOPODS_$key") ?: props[key]?.toString())?.takeIf(String::isNotBlank)
}
val releaseSigningAvailable = releaseSigningValues.values.all { it != null }
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
val requestsPublicReleaseArtifact = requestedTaskNames.any { taskName ->
    taskName in setOf("build", "assemble", "bundle") ||
        (taskName.contains("Release") &&
            (taskName.startsWith("bundle") || taskName.startsWith("assemble") ||
                taskName.startsWith("package") || taskName.startsWith("sign")))
}
if (requestsPublicReleaseArtifact) {
    if (!releaseSigningAvailable) {
        throw GradleException(
            "Public release artifacts must be signed with a stable distribution key. Configure " +
                "RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and " +
                "RELEASE_KEY_PASSWORD in local.properties. The Android debug key is not valid for distribution."
        )
    }

    val storePath = releaseSigningValues["RELEASE_STORE_FILE"].orEmpty()
    val configuredStore = File(storePath)
    val storeFile = if (configuredStore.isAbsolute) configuredStore else file(storePath)
    if (!storeFile.isFile) {
        throw GradleException("The configured distribution keystore does not exist: $storePath")
    }

    val storePassword = releaseSigningValues.getValue("RELEASE_STORE_PASSWORD")!!
    val keyAlias = releaseSigningValues.getValue("RELEASE_KEY_ALIAS")!!
    val certificate = try {
        KeyStore.getInstance("JKS").run {
            FileInputStream(storeFile).use { load(it, storePassword.toCharArray()) }
            getCertificate(keyAlias)
        }
    } catch (error: Exception) {
        throw GradleException("Could not verify the configured distribution signing key.", error)
    } ?: throw GradleException("The configured distribution signing alias has no certificate.")
    val certificateSha256 = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { byte -> "%02X".format(byte) }
    if (!certificateSha256.equals(expectedDistributionCertificateSha256, ignoreCase = true)) {
        throw GradleException(
            "Release builds must use the existing EchoPods distribution signing certificate. " +
                "Use scripts/build-foss-release.sh for GitHub APKs."
        )
    }
}

kotlin {
    compilerOptions {
        optIn.add(
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
}

android {
    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningValues.getValue("RELEASE_STORE_PASSWORD")!!
                keyAlias = releaseSigningValues.getValue("RELEASE_KEY_ALIAS")!!
                keyPassword = releaseSigningValues.getValue("RELEASE_KEY_PASSWORD")!!
            }
        }
    }
    namespace = "me.kavishdevar.librepods"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.galaxyairpods"
        minSdk = 31
        targetSdk = 37
        versionCode = 67
        versionName = appVersionName
        buildConfigField("Boolean", "PLAY_BUILD", "false")
        buildConfigField("Boolean", "STORE_BUILD", "false")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                }
            }
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            versionNameSuffix = "-debug"
        }
    }
    productFlavors {
        create("foss") {
            dimension = "env"
            buildConfigField("Boolean", "PLAY_BUILD", "false")
            buildConfigField("Boolean", "STORE_BUILD", "false")
        }
        create("play") {
            dimension = "env"
            buildConfigField("Boolean", "PLAY_BUILD", "true")
            buildConfigField("Boolean", "STORE_BUILD", "true")
        }
        create("galaxy") {
            dimension = "env"
            buildConfigField("Boolean", "PLAY_BUILD", "false")
            buildConfigField("Boolean", "STORE_BUILD", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            res.directories += "src/main/res-apple"
        }
    }

    ndkVersion = "30.0.14904198"

    flavorDimensions += "env"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.annotations)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.aboutlibraries)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.backdrop)
//    implementation(libs.hilt)
//    implementation(libs.hilt.compiler)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    add("playImplementation", libs.play.review)
    add("playImplementation", libs.play.review.ktx)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.google.mlkit.translate)
}

aboutLibraries {
    export {
        prettyPrint = true
        excludeFields = listOf("generated")
        outputFile = file("src/main/res/raw/aboutlibraries.json")
    }
}
