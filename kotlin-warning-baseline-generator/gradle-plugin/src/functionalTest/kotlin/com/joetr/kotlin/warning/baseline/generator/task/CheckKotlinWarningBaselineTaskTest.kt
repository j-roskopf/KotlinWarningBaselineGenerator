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
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.doesNotExist
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.failed
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.succeeded
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.task
import com.joetr.kotlin.warning.baseline.generator.infra.execute
import com.joetr.kotlin.warning.baseline.generator.infra.executeAndFail
import org.junit.Test

@Suppress("FunctionName")
class CheckKotlinWarningBaselineTaskTest {

    @Test
    fun `check task succeeds if no changes`() {
        val project = BasicAndroidProject.getComposeProject()

        val writeTask = ":android:releaseWriteKotlinWarningBaseline"
        val checkTask = ":android:releaseCheckKotlinWarningBaseline"

        val writeResult = project.execute(writeTask)
        val checkResult = project.execute(checkTask)

        assertThat(writeResult).task(writeTask).succeeded()
        assertThat(checkResult).task(checkTask).succeeded()
    }

    @Test
    fun `check task fails if new warning is added`() {
        val project = BasicAndroidProject.getComposeProject()

        val writeTask = ":android:releaseWriteKotlinWarningBaseline"
        val checkTask = ":android:releaseCheckKotlinWarningBaseline"

        val writeResult = project.execute(writeTask)

        // modify the source so the warning isn't in the baseline anymore
        project
            .projectDir("android")
            .resolve("src/main/kotlin/com/example/myapplication/TestComposable.kt")
            .toFile()
            .writeText(
                """
                package com.example.myapplication
                    
                class AndroidApp {
                        init {
                            println("hello") // this line offsets the null check to not be in the baseline
                            val test = "hello"
                            if(test != null) {
                                println(test)
                            }
                        }
                      }
                """
                    .trimIndent(),
            )

        val checkResult = project.executeAndFail(checkTask)

        assertThat(writeResult).task(writeTask).succeeded()
        assertThat(checkResult).task(checkTask).failed()
        assertThat(checkResult.output)
            .contains(
                "Found 1 warnings behind baseline",
            )
        assertThat(checkResult.output)
            .contains(
                "TestComposable.kt:7:16 Condition 'test != null' is always 'true",
            )
    }

    @Test
    fun `check generates no baseline if no warnings exist`() {
        val project =
            BasicAndroidProject.getComposeProject(
                androidProjectSource =
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

        val writeTask = ":android:releaseWriteKotlinWarningBaseline"
        val writeResult = project.execute(writeTask)
        assertThat(writeResult).task(writeTask).succeeded()

        val checkTask = ":android:releaseCheckKotlinWarningBaseline"
        val checkResult = project.execute(checkTask)
        assertThat(checkResult).task(checkTask).succeeded()
        val baselineFile =
            project.buildDir(":android").resolve("kotlin-warnings/warning-baseline-release.txt")
        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `check fails if warnings exist and no baseline exists`() {
        val project = BasicAndroidProject.getComposeProject()

        val checkTask = ":android:releaseCheckKotlinWarningBaseline"
        val checkResult = project.executeAndFail(checkTask)
        assertThat(checkResult).task(checkTask).failed()
        assertThat(checkResult.output)
            .contains(
                "Found 1 warnings behind baseline",
            )
        assertThat(checkResult.output)
            .contains(
                "TestComposable.kt:6:20 Condition 'test != null' is always 'true'",
            )
    }

    @Test
    fun `can clean and check multiple times`() {
        // https://github.com/j-roskopf/ComposeGuard/issues/42

        val project =
            BasicAndroidProject.getComposeProject()

        val debugGenerateTask = ":android:debugWriteKotlinWarningBaseline"
        val generateResult = project.execute("--build-cache", debugGenerateTask)
        assertThat(generateResult).task(debugGenerateTask).succeeded()

        val cleanTask = ":android:clean"
        val cleanResult = project.execute(cleanTask)
        assertThat(cleanResult).task(cleanTask).succeeded()

        val checkTask = ":android:debugCheckKotlinWarningBaseline"
        val checkResult = project.execute("--build-cache", checkTask)
        assertThat(checkResult).task(checkTask).succeeded()

        val newCleanResult = project.execute(cleanTask)
        assertThat(newCleanResult).task(cleanTask).succeeded()

        val newCheckResult = project.execute("--build-cache", checkTask)
        assertThat(newCheckResult).task(checkTask).succeeded()
    }
}
