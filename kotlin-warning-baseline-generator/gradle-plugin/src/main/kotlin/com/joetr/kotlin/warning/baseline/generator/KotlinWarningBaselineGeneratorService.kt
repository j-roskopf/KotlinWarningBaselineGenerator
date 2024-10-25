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

import com.joetr.kotlin.warning.baseline.generator.collector.WarningFileCollector
import org.gradle.api.GradleException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

public abstract class KotlinWarningBaselineGeneratorService :
    BuildService<BuildServiceParameters.None>, OperationCompletionListener, AutoCloseable {

    internal val baselineFilePaths: MutableMap<String, String> = ConcurrentHashMap()
    internal val warningFilePaths: MutableMap<String, String> = ConcurrentHashMap()
    internal val warningFileCollectors: MutableMap<String, WarningFileCollector> = ConcurrentHashMap()
    internal val listeners: MutableMap<String, BuildOperationListener> = ConcurrentHashMap()
    internal val managers: MutableMap<String, BuildOperationListenerManager> = ConcurrentHashMap()
    internal val tasksToComplete: MutableMap<String, Set<String>> = ConcurrentHashMap()
    private val tasksCompleted: MutableMap<String, Int> = ConcurrentHashMap()
    internal var isWriteTask: Boolean = false
    internal var isCheckTask: Boolean = false

    override fun onFinish(event: FinishEvent?) {
        if (event is TaskFinishEvent) {
            // [Task , app, releaseWriteKotlinWarningBaseline SUCCESS]
            // [Task , kotlinNpmCachesSetup SUCCESS]

            val eventSplit = event.displayName.split(":")
            if (eventSplit.size > 2) {
                val projectName = eventSplit[1]
                val taskStatusSplit = eventSplit[2].split(" ")
                val task = taskStatusSplit[0]
                val status = taskStatusSplit[1]
                if (isStatusComplete(status) && tasksToComplete[projectName]?.contains(task) == true) {
                    val current = tasksCompleted[projectName]
                    if (current == null) {
                        tasksCompleted[projectName] = 1
                    } else {
                        tasksCompleted[projectName] = current + 1
                    }

                    if (tasksToComplete[projectName]?.size == tasksCompleted[projectName]) {
                        val content =
                            warningFileCollectors[projectName]?.kotlinWarningsMap?.get(
                                projectName,
                            ) ?: emptySet()

                        val warningFileCollector = warningFileCollectors[projectName]
                        val filePathToWriteTo = if (isWriteTask) {
                            baselineFilePaths[projectName]
                        } else if (isCheckTask) {
                            warningFilePaths[projectName]
                        } else {
                            null
                        }
                        if (filePathToWriteTo != null) {
                            val baselineFile = File(filePathToWriteTo)
                            if (content.isNotEmpty()) {
                                warningFileCollector?.writeWarningsToFile(
                                    content,
                                    baselineFile,
                                )
                            } else {
                                if (baselineFile.exists()) {
                                    // had a baseline file previously, but no warnings, delete the file
                                    baselineFile.delete()
                                }
                            }
                        }

                        val listener = listeners[projectName]
                        val manager = managers[projectName]
                        if (listener != null && manager != null) {
                            manager.removeListener(listener)
                        } else {
                            throw GradleException("Listener added for $projectName but was not removed")
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        managers.forEach {
            val listener = listeners[it.key]
            if (listener != null) {
                it.value.removeListener(listener)
            }
        }
    }

    private fun isStatusComplete(status: String): Boolean {
        // SUCCESS, UP-TO-DATE, skipped
        return status.equals("SUCCESS", ignoreCase = true) ||
            status.equals("UP-TO-DATE", ignoreCase = true) ||
            status.equals("skipped", ignoreCase = true)
    }
}
