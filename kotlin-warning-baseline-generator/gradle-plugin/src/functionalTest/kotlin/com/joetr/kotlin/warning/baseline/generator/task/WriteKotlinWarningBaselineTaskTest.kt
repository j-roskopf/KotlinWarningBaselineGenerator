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
package com.joetr.kotlin.warning.baseline.generator.task

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.exists
import com.joetr.kotlin.warning.baseline.generator.BasicAndroidProject
import com.joetr.kotlin.warning.baseline.generator.infra.RetryRule
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.doesNotExist
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.succeeded
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.task
import com.joetr.kotlin.warning.baseline.generator.infra.execute
import com.joetr.kotlin.warning.baseline.generator.infra.executeAndFail
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.readText

@Suppress("FunctionName")
class WriteKotlinWarningBaselineTaskTest {

    @get:Rule
    val retryRule = RetryRule(retryCount = 5)

    @Test
    fun `write task generates baseline`() {
        val project = BasicAndroidProject.getComposeProject()

        val task = ":android:releaseWriteKotlinWarningBaseline"
        val result = project.execute(task)

        assertThat(result).task(task).succeeded()
        val baselineFile = project.projectDir(":android").resolve("warning-baseline-release-android.txt")
        assertThat(baselineFile.toFile()).exists()
        assertThat(baselineFile.readText())
            .contains("TestComposable.kt:6:20 Condition 'test != null' is always 'true")

        // only relative paths
        assertThat(baselineFile.readText()).doesNotContain("w: file:///")
    }

    @Test
    fun `if all files are cleaned up in baseline, baseline is deleted`() {
        val project = BasicAndroidProject.getComposeProject()

        val generateTask = ":android:releaseWriteKotlinWarningBaseline"
        val generateResult = project.execute(generateTask)

        assertThat(generateResult).task(generateTask).succeeded()
        val baselineFile = project.projectDir(":android").resolve("warning-baseline-release-android.txt")
        assertThat(baselineFile.toFile()).exists()
        assertThat(baselineFile.readText())
            .contains("TestComposable.kt:6:20 Condition 'test != null' is always 'true")

        project
            .projectDir("android")
            .resolve("src/main/kotlin/com/example/myapplication/TestComposable.kt")
            .toFile()
            .writeText(
                """
                package com.example.myapplication
                    
                class AndroidApp {
                        init {
                            val test = "hello"
                            println(test)
                        }
                      }
                """
                    .trimIndent(),
            )

        val newGenerateResult = project.execute(generateTask)

        assertThat(newGenerateResult).task(generateTask).succeeded()
        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `write generates no baseline if no warnings exist`() {
        val project = BasicAndroidProject.getComposeProject(
            androidProjectSource = """
                     package com.example.myapplication
                     
                     class AndroidApp {
                        init {
                            val test = "hello"
                            println(test)
                        }
                      }
            """.trimIndent(),
        )

        val task = ":android:releaseWriteKotlinWarningBaseline"
        val result = project.execute(task)
        assertThat(result).task(task).succeeded()
        val baselineFile = project.projectDir(":android").resolve("warning-baseline-release.txt")
        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `write generates no baseline if no source exist`() {
        val project =
            BasicAndroidProject.getComposeProject(
                androidProjectSource = null,
            )

        val task = ":android:releaseWriteKotlinWarningBaseline"
        val result = project.executeAndFail(task)
        assertThat(result.output)
            .contains(
                "Cannot locate tasks that match ':android:releaseWriteKotlinWarningBaseline' as task 'releaseWriteKotlinWarningBaseline' not found in project ':android'",
            )
    }

    @Test
    fun `subsequent writes trigger update to baseline with configuration cache enabled`() {
        val project = BasicAndroidProject.getComposeProject(
            androidProjectSource = """
                     package com.example.myapplication
                     
                     class AndroidApp {
                        init {
                            val test = "hello"
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
            """.trimIndent(),
        )

        val task = ":android:releaseWriteKotlinWarningBaseline"
        val result = project.execute("--configuration-cache", task)
        assertThat(result).task(task).succeeded()

        val baselineFile = project.projectDir(":android").resolve("warning-baseline-release-android.txt")
        assertThat(baselineFile).exists()

        var baselineText = baselineFile.readText()
        assertThat(baselineText).contains("TestComposable.kt:6:11 Condition 'test != null' is always 'true'")

        project
            .projectDir("android")
            .resolve("src/main/kotlin/com/example/myapplication/TestComposable.kt")
            .toFile()
            .writeText(
                """
                package com.example.myapplication
                    
                class AndroidApp {
                        init {
                            println("hello") // this line offsets the null check
                            val test = "hello"
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
                """
                    .trimIndent(),
            )

        val newResult = project.execute("--configuration-cache", task)
        assertThat(newResult).task(task).succeeded()

        assertThat(baselineFile).exists()

        baselineText = baselineFile.readText()
        assertThat(baselineText).contains("TestComposable.kt:7:16 Condition 'test != null' is always 'true'")

        project
            .projectDir("android")
            .resolve("src/main/kotlin/com/example/myapplication/TestComposable.kt")
            .toFile()
            .writeText(
                """
                package com.example.myapplication
                    
                class AndroidApp {
                        init {
                            println("hello") // this line offsets the null check
                            println("hello") // this line offsets the null check
                            val test = "hello"
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
                """
                    .trimIndent(),
            )

        val newestResult = project.execute("--configuration-cache", task)
        assertThat(newestResult).task(task).succeeded()

        assertThat(baselineFile).exists()

        baselineText = baselineFile.readText()
        assertThat(baselineText).contains("TestComposable.kt:8:16 Condition 'test != null' is always 'true'")
    }
}
