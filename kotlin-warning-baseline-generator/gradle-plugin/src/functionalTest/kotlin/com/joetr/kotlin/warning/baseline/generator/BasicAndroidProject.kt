/**
 * MIT License
 *
 * Copyright (c) 2024 Joe Roskopf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.joetr.kotlin.warning.baseline.generator

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.SourceType
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.android.AndroidBlock
import com.autonomousapps.kit.gradle.android.CompileOptions
import com.autonomousapps.kit.gradle.android.DefaultConfig
import com.joetr.kotlin.warning.baseline.generator.infra.BuildFixture
import com.joetr.kotlin.warning.baseline.generator.infra.Plugins
import com.joetr.kotlin.warning.baseline.generator.infra.Plugins.composePlugin
import com.joetr.kotlin.warning.baseline.generator.infra.Plugins.kotlinAndroid
import org.gradle.api.JavaVersion

object BasicAndroidProject {
    fun getComposeProject(
        additionalBuildScriptForAndroidSubProject: String = "",
        additionalDependenciesForAndroidSubProject: String = "",
        additionalPluginsForAndroidSubProject: List<Plugin> = emptyList(),
        kotlinVersion: String = Plugins.KOTLIN_VERSION_1_9_22,
        includeEmptyModule: Boolean = false,
        applyMultiplatform: Boolean = false,
        androidProjectSource: String? = defaultAndroidAppSource(),
    ): GradleProject {
        val includeKotlinCompilerExtensionVersion = kotlinVersion.startsWith("1")
        val script =
            """
            android.buildFeatures.compose = true
            
            $additionalBuildScriptForAndroidSubProject

            dependencies {
                    implementation("androidx.core:core-ktx:1.10.1")
                    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
                    implementation("androidx.activity:activity-compose:1.7.0")
                    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
                    implementation("androidx.compose.ui:ui")
                    implementation("androidx.compose.ui:ui-graphics")
                    implementation("androidx.compose.ui:ui-tooling-preview")
                    implementation("androidx.compose.material3:material3")
                    testImplementation("junit:junit:4.13.2")
                    androidTestImplementation("junit:junit:4.13.2")
                    $additionalDependenciesForAndroidSubProject
            }
            
            """.trimIndent() +
                if (includeKotlinCompilerExtensionVersion) {
                    """
                    android.composeOptions.kotlinCompilerExtensionVersion = "1.5.10"
                    """.trimIndent()
                } else {
                    ""
                }

        val plugins =
            if (applyMultiplatform) {
                listOf(Plugin(id = "org.jetbrains.kotlin.multiplatform", apply = true))
            } else {
                listOf(kotlinAndroid(kotlinVersion = kotlinVersion))
            } +
                listOf(
                    BuildFixture.ANDROID_APP_PLUGIN,
                    BuildFixture.REPORT_GEN_PLUGIN,
                ) + additionalPluginsForAndroidSubProject +
                if (kotlinVersion.startsWith("2")) {
                    listOf(composePlugin(kotlinVersion = kotlinVersion))
                } else {
                    emptyList()
                }

        val project =
            BuildFixture().build(
                applyMultiplatform = applyMultiplatform,
                script =
                """
                    buildscript {
                      repositories {
                        google()
                        mavenCentral()
                      }
                      dependencies {
                        classpath("com.android.tools.build:gradle:8.2.0")
                        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
                      }
                    }
                """.trimIndent(),
                builder = {
                    withAndroidLibProject(
                        name = "android",
                        packageName = "com.example.myapplication",
                    ) {
                        withBuildScript {
                            plugins(plugins)
                            android =
                                AndroidBlock(
                                    namespace = "com.example.myapplication",
                                    compileSdkVersion = 34,
                                    defaultConfig =
                                    DefaultConfig(
                                        applicationId = "com.example.myapplication",
                                        minSdkVersion = 21,
                                        targetSdkVersion = 34,
                                        versionCode = 1,
                                        versionName = "1.0",
                                    ),
                                    compileOptions =
                                    CompileOptions(
                                        sourceCompatibility = JavaVersion.VERSION_17,
                                        targetCompatibility = JavaVersion.VERSION_17,
                                    ),
                                )
                            withKotlin(script)

                            androidProjectSource?.let {
                                sources =
                                    listOf(
                                        Source(
                                            sourceType = SourceType.KOTLIN,
                                            name = "TestComposable",
                                            path = "com/example/myapplication",
                                            source = it,
                                        ),
                                    )
                            }
                        }
                    }

                    if (includeEmptyModule) {
                        withAndroidLibProject(
                            name = "android-empty",
                            packageName = "com.example.myapplication",
                        ) {
                            withBuildScript {
                                plugins(plugins)
                                android =
                                    AndroidBlock(
                                        namespace = "com.example.myapplication",
                                        compileSdkVersion = 34,
                                        defaultConfig =
                                        DefaultConfig(
                                            applicationId = "com.example.myapplication",
                                            minSdkVersion = 21,
                                            targetSdkVersion = 34,
                                            versionCode = 1,
                                            versionName = "1.0",
                                        ),
                                        compileOptions =
                                        CompileOptions(
                                            sourceCompatibility = JavaVersion.VERSION_17,
                                            targetCompatibility = JavaVersion.VERSION_17,
                                        ),
                                    )
                                withKotlin(script)
                            }
                        }
                    }
                },
                kotlinVersion = kotlinVersion,
            )

        return project
    }

    private fun defaultAndroidAppSource(): String {
        return """
            package com.example.myapplication
            
            class AndroidApp {
                        init {
                            val test = "hello"
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
        """.trimIndent()
    }
}
