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
import com.joetr.kotlin.warning.baseline.generator.BasicAndroidProject
import com.joetr.kotlin.warning.baseline.generator.infra.RetryRule
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.succeeded
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.task
import com.joetr.kotlin.warning.baseline.generator.infra.execute
import com.joetr.kotlin.warning.baseline.generator.infra.executeAndFail
import org.junit.Rule
import org.junit.Test

@Suppress("FunctionName")
class MultiplatformKotlinWarningBaselineTaskTest {

    @get:Rule
    val retryRule = RetryRule(retryCount = 5)

    @Test
    fun `multiplatform android`() {
        val project =
            BasicAndroidProject.getComposeProject(
                applyMultiplatform = true,
                additionalBuildScriptForAndroidSubProject =
                """
                    kotlin {
                        androidTarget()
                    }
                """.trimIndent(),
            )

        val src = project.projectDir("android").resolve("src/main").toFile()
        src.renameTo(project.projectDir("android").resolve("src/androidMain").toFile())

        // generate golden
        val generateTask = ":android:androidReleaseWriteKotlinWarningBaseline"
        val generateResult = project.execute(generateTask)
        assertThat(generateResult).task(generateTask).succeeded()

        // check
        val checkTask = ":android:androidReleaseCheckKotlinWarningBaseline"
        val checkResult = project.execute(checkTask)
        assertThat(checkResult).task(checkTask).succeeded()

        project.projectDir("android").resolve("src/androidMain/kotlin/com/example/myapplication/TestComposable.kt").toFile()
            .writeText(
                """
            package com.example.myapplication
            
            class AndroidApp {
                        init {
                            val test = "hello"
                            println("hello") // pushing warning down
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
                """,
            )

        // Assert check fails with new unstable parameter
        val newCheckResult = project.executeAndFail(checkTask)
        assertThat(newCheckResult.output).contains("TestComposable.kt:8:32 Condition 'test != null' is always 'true'")
        assertThat(newCheckResult.output)
            .contains(
                "Found 1 warnings behind baseline",
            )
    }
}
