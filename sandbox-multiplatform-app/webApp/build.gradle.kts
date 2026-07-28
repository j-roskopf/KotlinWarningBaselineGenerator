@file:Suppress("DEPRECATION")

import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("com.joetr.kotlin.warning.baseline.generator")
}

val copyJsResources = tasks.create("copyJsResourcesWorkaround", Copy::class.java) {
    from(project(":shared").file("src/commonMain/composeResources"))
    into("build/processedResources/js/main")
}

val copyWasmResources = tasks.create("copyWasmResourcesWorkaround", Copy::class.java) {
    from(project(":shared").file("src/commonMain/composeResources"))
    into("build/processedResources/wasmJs/main")
}

afterEvaluate {
    project.tasks.getByName("jsProcessResources").finalizedBy(copyJsResources)
    project.tasks.getByName("wasmJsProcessResources").finalizedBy(copyWasmResources)
    project.tasks.getByName("wasmJsDevelopmentExecutableCompileSync").dependsOn(copyWasmResources)
}

val ktorVersion = extra["ktor.version"]

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "imageviewer.js"
            }
        }
        binaries.executable()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Uncomment and configure this if you want to open a browser different from the system default 
                    // open = mapOf(
                    //     "app" to mapOf(
                    //         "name" to "google chrome"
                    //     )
                    // )

                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.rootDir.path)
                        add(project.rootDir.path + "/shared/")
                        add(project.rootDir.path + "/nonAndroidMain/")
                        add(project.rootDir.path + "/webApp/")
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsWasmMain by creating {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
            }
        }
        val jsMain by getting {
            dependsOn(jsWasmMain)
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-js:$ktorVersion")
            }
        }
        val wasmJsMain by getting {
            dependsOn(jsWasmMain)
        }
    }
}
