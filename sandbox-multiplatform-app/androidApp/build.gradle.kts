import com.joetr.kotlin.warning.baseline.generator.KotlinCompileTask

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("com.joetr.kotlin.warning.baseline.generator")
}

kotlinWarningBaselineGenerator {
    kotlinCompileTasksToConfigure.set(
        listOf(
            KotlinCompileTask.MAIN,
            KotlinCompileTask.UNIT_TEST,
            KotlinCompileTask.ANDROID_TEST,
        )
    )
}

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
            }
        }
    }
}

android {
    namespace = "example.imageviewer"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.jetbrains.Imageviewer"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
dependencies {
    testImplementation("org.testng:testng:6.9.6")
    androidTestImplementation("org.testng:testng:6.9.6")
}
