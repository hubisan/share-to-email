import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ch.hubisan.sharetoemail"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.hubisan.sharetoemail"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->

        val versionName = variant.outputs.first().versionName.orNull ?: "unknown"
        val capName = variant.name.replaceFirstChar { it.uppercase() }

        val apkFolder = variant.artifacts.get(SingleArtifact.APK)

        val copyTask = tasks.register<Copy>("copy${capName}ApkWithName") {
            from(apkFolder)
            include("*.apk")
            into(layout.buildDirectory.dir("outputs/renamed-apk/${variant.name}"))
            rename { "share-to-email-$versionName.apk" }
        }

        // <- wichtig: NICHT named(...)
        tasks.matching { it.name == "assemble$capName" }.configureEach {
            finalizedBy(copyTask)
        }

        // optionaler Fallback, falls Studio anders baut:
        tasks.matching { it.name == "package$capName" }.configureEach {
            finalizedBy(copyTask)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.org.json)

    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
