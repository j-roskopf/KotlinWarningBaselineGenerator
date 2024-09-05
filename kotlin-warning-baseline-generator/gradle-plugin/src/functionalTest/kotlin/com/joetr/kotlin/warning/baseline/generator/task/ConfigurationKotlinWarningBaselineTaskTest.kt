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
import assertk.assertions.exists
import com.joetr.kotlin.warning.baseline.generator.BasicAndroidProject
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.failed
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.succeeded
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.task
import com.joetr.kotlin.warning.baseline.generator.infra.execute
import com.joetr.kotlin.warning.baseline.generator.infra.executeAndFail
import org.junit.Test
import java.io.File
import kotlin.io.path.readText

@Suppress("FunctionName")
class ConfigurationKotlinWarningBaselineTaskTest {

    @Test
    fun `dynamic properties configuration with unit test`() {
        val project =
            BasicAndroidProject.getComposeProject(
                additionalBuildScriptForAndroidSubProject =
                """
                    kotlinWarningBaselineGenerator {
                        kotlinCompileTasksToConfigure = listOf(
                            com.joetr.kotlin.warning.baseline.generator.KotlinCompileTask.MAIN,
                            com.joetr.kotlin.warning.baseline.generator.KotlinCompileTask.UNIT_TEST,
                            com.joetr.kotlin.warning.baseline.generator.KotlinCompileTask.ANDROID_TEST,
                        )
                    }
                """.trimIndent(),
            )
        val generateTask = ":android:releaseWriteKotlinWarningBaseline"

        val unitTestDirectory =
            project.projectDir("android").resolve("src/test/kotlin/com/example/myapplication").toFile()

        val androidTestDirectory = project.projectDir("android").resolve("src/androidTest/kotlin/com/example/myapplication").toFile()

        unitTestDirectory.mkdirs()
        androidTestDirectory.mkdirs()

        val unitTestFile = File(unitTestDirectory, "ExampleUnitTest.kt")
        val androidTestFile = File(androidTestDirectory, "ExampleInstrumentedTest.kt")

        unitTestFile.writeText(
            """
            package com.example.myapplication
            
            import org.junit.Test
            import org.junit.Assert.*

            class ExampleUnitTest {
                @Test
                fun addition_isCorrect() {
                    val testSrcSet = 4
            
                    if(testSrcSet != null) {
                        println("hello")
                    }
                    assertEquals(4, 2 + 2)
                }
            }
            """.trimIndent(),
        )

        androidTestFile.writeText(
            """
            package com.example.myapplication
            
            import org.junit.Test
            
            class ExampleInstrumentedTest {
                @Test
                fun androidTest() {
                    val androidTest = "hello"
            
                    if(androidTest != null) {
                        println(androidTest)
                    }
                }
            }
            """.trimIndent(),
        )

        // generate golden
        val generateResult = project.execute(generateTask)
        assertThat(generateResult).task(generateTask).succeeded()

        val baselineFile = project.projectDir(":android").resolve("warning-baseline-release-android.txt")
        assertThat(baselineFile.toFile()).exists()

        val baselineText = baselineFile.readText()

        // should contain test src set
        assertThat(baselineText).contains("TestComposable.kt:6:20 Condition 'test != null' is always 'true'")
        assertThat(baselineText).contains("ExampleUnitTest.kt:11:12 Condition 'testSrcSet != null' is always 'true'")
        assertThat(baselineText).contains("ExampleInstrumentedTest.kt:10:12 Condition 'androidTest != null' is always 'true'")

        // modify test src
        unitTestFile.writeText(
            """
            package com.example.myapplication
            
            import org.junit.Test
            import org.junit.Assert.*

            class ExampleUnitTest {
                @Test
                fun addition_isCorrect() {
                    val testSrcSet = 4
            
                    println("hello") // pushes warning down
                    if(testSrcSet != null) {
                        println("hello")
                    }
                    assertEquals(4, 2 + 2)
                }
            }
            """.trimIndent(),
        )

        // assert check fails
        val checkTask = ":android:releaseCheckKotlinWarningBaseline"
        val checkResult = project.executeAndFail(checkTask)
        assertThat(checkResult).task(checkTask).failed()
        assertThat(checkResult.output)
            .contains(
                "Found 1 warnings behind baseline",
            )
        assertThat(checkResult.output)
            .contains(
                "ExampleUnitTest.kt:12:12 Condition 'testSrcSet != null' is always 'true'",
            )

        // regenerate baseline
        val newUnitTestGenerateResult = project.execute(generateTask)
        assertThat(newUnitTestGenerateResult).task(generateTask).succeeded()

        // modify android test src
        androidTestFile.writeText(
            """
            package com.example.myapplication
            
            import org.junit.Test
            
            class ExampleInstrumentedTest {
                @Test
                fun androidTest() {
                    val androidTest = "hello"
            
                    println("hello") // pushes down
                    if(androidTest != null) {
                        println(androidTest)
                    }
                }
            }
            """.trimIndent(),
        )

        val androidTestCheckResult = project.executeAndFail(checkTask)
        assertThat(androidTestCheckResult).task(checkTask).failed()
        assertThat(androidTestCheckResult.output)
            .contains(
                "Found 1 warnings behind baseline",
            )
        assertThat(androidTestCheckResult.output)
            .contains(
                "ExampleInstrumentedTest.kt:11:12 Condition 'androidTest != null' is always 'true'",
            )
    }
}
