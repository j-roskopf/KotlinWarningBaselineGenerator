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
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.doesNotExist
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.succeeded
import com.joetr.kotlin.warning.baseline.generator.infra.asserts.task
import com.joetr.kotlin.warning.baseline.generator.infra.execute
import org.junit.Test

@Suppress("FunctionName")
class RemoveKotlinWarningBaselineTaskTest {

    @Test
    fun `remove task cleans up warning files`() {
        val project = BasicAndroidProject.getComposeProject()

        val writeTask = ":android:releaseWriteKotlinWarningBaseline"
        val checkTask = ":android:releaseCheckKotlinWarningBaseline"
        val removeTask = ":android:removeKotlinWarningBaseline"

        val writeResult = project.execute(writeTask)
        val checkResult = project.execute(checkTask)

        assertThat(writeResult).task(writeTask).succeeded()
        assertThat(checkResult).task(checkTask).succeeded()

        assertThat(project.projectDir(":android").resolve("warning-baseline-release-android.txt")).exists()
        assertThat(project.buildDir(":android").resolve("kotlin-warning/warning-baseline-release-android.txt")).exists()

        val removeResult = project.execute(removeTask)
        assertThat(removeResult).task(removeTask).succeeded()

        assertThat(project.projectDir(":android").resolve("warning-baseline-release-android.txt")).doesNotExist()
        assertThat(project.buildDir(":android").resolve("kotlin-warning/warning-baseline-release-android.txt")).doesNotExist()
    }
}
