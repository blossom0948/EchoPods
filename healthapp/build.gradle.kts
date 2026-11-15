import java.util.Properties
import java.io.File

val signingPropsFile = rootProject.file("local.properties")
val signingProps = Properties().apply { if (signingPropsFile.exists()) load(signingPropsFile.inputStream()) }
val distributionKeys = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
val distributionValues = distributionKeys.associateWith { key ->
    (System.getenv("ECHOPODS_$key") ?: signingProps[key]?.toString())?.takeIf(String::isNotBlank)
}
val hasDistributionSigning = distributionValues.values.all { it != null }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.galaxyairpods.health"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.galaxyairpods.health"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasDistributionSigning) {
            create("release") {
                val configuredStore = File(distributionValues.getValue("RELEASE_STORE_FILE")!!)
                storeFile = if (configuredStore.isAbsolute) configuredStore else rootProject.file("app/${configuredStore.path}")
                storePassword = distributionValues.getValue("RELEASE_STORE_PASSWORD")!!
                keyAlias = distributionValues.getValue("RELEASE_KEY_ALIAS")!!
                keyPassword = distributionValues.getValue("RELEASE_KEY_PASSWORD")!!
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = if (hasDistributionSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            signingConfig = if (hasDistributionSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.material3)
    implementation(libs.haze)
    implementation(libs.backdrop)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.health.connect:connect-client:1.1.0")
}
