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

import com.joetr.kotlin.warning.baseline.generator.ext.readSetOfLines
import com.joetr.kotlin.warning.baseline.generator.ext.readWarningLines
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Check task for validating current state of the code against baseline
 *
 * warning file and baseline can be missing in scenarios where a module has no warnings
 */
internal abstract class CheckKotlinWarningBaselineTask : DefaultTask() {

    @get:InputFile @get:Optional
    abstract val baselineFile: Property<File>

    @get:Input @get:Optional
    abstract val warningFilePath: Property<String>

    /**
     * All kotlin sources for the module
     *
     * Marked as an input so we can re-run when a change occurs
     */
    @get:InputFiles
    @get:Optional
    abstract val kotlinSourceSets: ListProperty<File>

    @TaskAction
    fun check() {
        val warningFile = File(warningFilePath.get())
        val warningSet = if (warningFile.exists()) {
            warningFile.readWarningLines().toSet()
        } else {
            emptySet()
        }

        val baselineSet =
            if (baselineFile.isPresent && baselineFile.get().exists()) {
                baselineFile.get().readSetOfLines()
            } else {
                println("WARNING: No baseline file detected. Assuming no baseline.")
                emptySet()
            }

        val diff = warningSet - baselineSet

        if (diff.isNotEmpty()) {
            val text =
                diff.joinToString(
                    prefix =
                    """
                    Found ${diff.size} warnings behind baseline:

                    Please try and address the warnings listed.
                    As a last resort, you can regenerate the baseline
                    `./gradlew :<PROJECT_NAME>:<variant>WriteKotlinWarningBaseline`

                    More info: https://dev.azure.com/WBA/Digital/_git/wag-mobileapp-gradle-plugin?anchor=warnings-as-errors


                """
                        .trimIndent(),
                    separator = "\n",
                )
            throw GradleException(text)
        }
    }
}
