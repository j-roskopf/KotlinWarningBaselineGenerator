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
@file:Suppress("KotlinConstantConditions", "ObjectLiteralToLambda")


package com.joetr.kotlin.warning.baseline.generator

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.joetr.kotlin.warning.baseline.generator.collector.WarningFileCollector
import com.joetr.kotlin.warning.baseline.generator.task.CheckKotlinWarningBaselineTask
import com.joetr.kotlin.warning.baseline.generator.task.RemoveKotlinWarningBaselineTask
import com.joetr.kotlin.warning.baseline.generator.task.WriteKotlinWarningBaselineTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceSpec
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*
import kotlin.io.path.pathString

internal const val WRITE_TASK_NAME = "WriteKotlinWarningBaseline"
private const val CHECK_TASK_NAME = "CheckKotlinWarningBaseline"
private const val WARNING_BASELINE_FILE_PREFIX = "warning-baseline"

private const val DEFAULT_VARIANT_ANDROID_TARGET = "release"
private val supportedVariantsAndroidTarget =
    listOf("staging", "debug", DEFAULT_VARIANT_ANDROID_TARGET)

/**
 * Can be called to configure a custom 'warnings as errors' with baselines.
 *
 * This is spiritually similar to `allWarningsAsErrors` or the `-Werror` flag, but differs in that a
 * baseline is generated and warnings that appear in the baseline are not flagged.
 */
internal fun Project.configureKotlinWarningBaselineForAndroid(eventListenerRegistry: BuildEventsListenerRegistry) {
    // if there are no kotlin sources, there's nothing for us to do
    if (hasNonEmptyKotlinSourceSets().not()) {
        return
    }

    val extension = KotlinWarningBaselineExtension.get(this)

    val warningFileCollector = WarningFileCollector().apply {
        projectName = project.name
    }

    // Register the BuildService
    val kotlinWarningBaselineGeneratorService = gradle.sharedServices.registerIfAbsent(
        "kotlinWarningBaselineGeneratorService",
        KotlinWarningBaselineGeneratorService::class.java,
        object : Action<BuildServiceSpec<BuildServiceParameters.None>> {
            override fun execute(t: BuildServiceSpec<BuildServiceParameters.None>) {
            }
        },
    )

    kotlinWarningBaselineGeneratorService.get().warningFileCollectors[project.name] =
        warningFileCollector
    kotlinWarningBaselineGeneratorService.get().managers[project.name] =
        project.gradle.serviceOf<BuildOperationListenerManager>()

    eventListenerRegistry.onTaskCompletion(kotlinWarningBaselineGeneratorService)

    // register write + check tasks for library modules
    extensions.findByType<LibraryAndroidComponentsExtension>()?.beforeVariants { variant ->
        setupDataForAndroidTarget(
            variant = variant.name,
            target = "android",
            extension = extension,
            kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
            warningFileCollector = warningFileCollector,
        )
    }

    // register write + check tasks for app module
    extensions.findByType<ApplicationAndroidComponentsExtension>()?.beforeVariants { variant ->
        setupDataForAndroidTarget(
            variant = variant.name,
            target = "android",
            extension = extension,
            kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
            warningFileCollector = warningFileCollector,
        )
    }

    registerRemoveTask()
}

internal fun Project.configureKotlinWarningBaselineForMultiplatform(eventListenerRegistry: BuildEventsListenerRegistry) {
    val extension = KotlinWarningBaselineExtension.get(this)

    val kotlinWarningBaselineGeneratorService = gradle.sharedServices.registerIfAbsent(
        "kotlinWarningBaselineGeneratorService",
        KotlinWarningBaselineGeneratorService::class.java,
        object : Action<BuildServiceSpec<BuildServiceParameters.None>> {
            override fun execute(t: BuildServiceSpec<BuildServiceParameters.None>) {
            }
        },
    )

    val warningFileCollector = WarningFileCollector().apply {
        projectName = project.name
    }

    kotlinWarningBaselineGeneratorService.get().warningFileCollectors[project.name] =
        warningFileCollector
    kotlinWarningBaselineGeneratorService.get().managers[project.name] =
        project.gradle.serviceOf<BuildOperationListenerManager>()

    eventListenerRegistry.onTaskCompletion(kotlinWarningBaselineGeneratorService)

    val multiplatformExtension = extensions.findByType<KotlinMultiplatformExtension>()
    val androidTargets = multiplatformExtension?.targets?.withType(KotlinAndroidTarget::class.java)
    androidTargets?.all(
        object : Action<KotlinAndroidTarget> {
            override fun execute(kotlinAndroidTarget: KotlinAndroidTarget) {
                kotlinAndroidTarget.compilations.all(
                    object : Action<KotlinJvmAndroidCompilation> {
                        override fun execute(kotlinJvmAndroidCompilation: KotlinJvmAndroidCompilation) {
                            val mappedAndroidVariant = when {
                                kotlinJvmAndroidCompilation.androidVariant.name.contains("UnitTest") -> KotlinCompileTask.UNIT_TEST
                                kotlinJvmAndroidCompilation.androidVariant.name.contains("AndroidTest") -> KotlinCompileTask.ANDROID_TEST
                                kotlinJvmAndroidCompilation.androidVariant.name in supportedVariantsAndroidTarget -> KotlinCompileTask.MAIN
                                else -> null
                            }

                            if (mappedAndroidVariant in extension.kotlinCompileTasksToConfigure.get()) {
                                setupDataForMultiplatformTarget(
                                    variant = kotlinJvmAndroidCompilation.androidVariant.name,
                                    target = kotlinAndroidTarget.name,
                                    kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
                                    warningFileCollector = warningFileCollector,
                                )
                            }
                        }
                    },
                )
            }
        },
    )

    val jvmTargets =
        multiplatformExtension?.targets?.withType(KotlinJvmTarget::class.java)
    jvmTargets?.all(
        object : Action<KotlinJvmTarget> {
            override fun execute(kotlinJvmTarget: KotlinJvmTarget) {
                setupDataForMultiplatformTarget(
                    variant = "",
                    target = kotlinJvmTarget.name,
                    kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
                    warningFileCollector = warningFileCollector,
                )
            }
        },
    )

    val nativeTargets =
        multiplatformExtension?.targets?.withType(KotlinNativeTarget::class.java)
    nativeTargets?.all(
        object : Action<KotlinNativeTarget> {
            override fun execute(kotlinNativeTarget: KotlinNativeTarget) {
                setupDataForMultiplatformTarget(
                    variant = "",
                    target = kotlinNativeTarget.name,
                    kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
                    warningFileCollector = warningFileCollector,
                )
            }
        },
    )

    val jsTargets =
        multiplatformExtension?.targets?.withType(KotlinJsTarget::class.java)
    jsTargets?.all(
        object : Action<KotlinJsTarget> {
            override fun execute(kotlinJsTarget: KotlinJsTarget) {
                setupDataForMultiplatformTarget(
                    variant = "",
                    target = kotlinJsTarget.name,
                    kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
                    warningFileCollector = warningFileCollector,
                )
            }
        },
    )

    val irTargets =
        multiplatformExtension?.targets?.withType(KotlinJsIrTarget::class.java)
    irTargets?.all(
        object : Action<KotlinJsIrTarget> {
            override fun execute(kotlinJsTarget: KotlinJsIrTarget) {
                setupDataForMultiplatformTarget(
                    variant = "",
                    target = kotlinJsTarget.name,
                    kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
                    warningFileCollector = warningFileCollector,
                )
            }
        },
    )

    registerRemoveTask()
}

private fun Project.setupDataForAndroidTarget(
    variant: String,
    target: String,
    extension: KotlinWarningBaselineExtension,
    kotlinWarningBaselineGeneratorService: Provider<KotlinWarningBaselineGeneratorService>,
    warningFileCollector: WarningFileCollector,
) {
    val baselineTextFile = warningBaselineFileName(variant = variant, target = target)
    val baselineFile = File(project.layout.projectDirectory.asFile, baselineTextFile)
    val warningFile =
        File(project.layout.buildDirectory.asFile.get(), "kotlin-warning/$baselineTextFile")
    val compileTaskDependsOn = extension.kotlinCompileTasksToConfigure.get().map {
        taskNameFromCompileTaskSource(
            kotlinCompileTask = it,
            variant = variant,
        )
    }.toSet()

    val variantToDependOn = getVariantFromStartTask(
        taskNames = gradle.startParameter.taskNames,
    )

    kotlinWarningBaselineGeneratorService.get().warningFilePaths[project.name] =
        warningFile.absolutePath
    kotlinWarningBaselineGeneratorService.get().baselineFilePaths[project.name] =
        baselineFile.absolutePath

    kotlinWarningBaselineGeneratorService.get().isWriteTask =
        gradle.startParameter.taskNames.any { it.contains(WRITE_TASK_NAME) }
    kotlinWarningBaselineGeneratorService.get().isCheckTask =
        gradle.startParameter.taskNames.any { it.contains(CHECK_TASK_NAME) }

    addCompileTaskToService(
        compileTaskDependsOn = compileTaskDependsOn,
        service = kotlinWarningBaselineGeneratorService.get(),
        variantToDependOn = variantToDependOn,
        skipValidation = false,
    )

    configureTasks(
        variant = variant,
        target = "android",
        baselineFile = baselineFile,
        warningFile = warningFile,
        kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
        warningFileCollector = warningFileCollector,
        compileTaskDependsOn = compileTaskDependsOn,
    )
}

private fun Project.setupDataForMultiplatformTarget(
    variant: String,
    target: String,
    kotlinWarningBaselineGeneratorService: Provider<KotlinWarningBaselineGeneratorService>,
    warningFileCollector: WarningFileCollector,
) {
    val baselineTextFile = warningBaselineFileName(variant = variant, target = target)
    val baselineFile = File(project.layout.projectDirectory.asFile, baselineTextFile)
    val warningFile =
        File(project.layout.buildDirectory.asFile.get(), "kotlin-warning/$baselineTextFile")

    val compileTaskDependsOn =
        setOf("compile${variant.capitalizeCompat()}Kotlin${target.capitalizeCompat()}")

    if (gradle.startParameter.taskNames.any {
            it.contains(target, ignoreCase = true)
        }
    ) {
        if (target.contains("android", ignoreCase = true)) {
            val variantToDependOn = getVariantFromStartTask(
                taskNames = gradle.startParameter.taskNames,
            )
            addCompileTaskToService(
                compileTaskDependsOn = compileTaskDependsOn,
                service = kotlinWarningBaselineGeneratorService.get(),
                variantToDependOn = variantToDependOn,
                skipValidation = false,
            )
        } else {
            addTaskToService(compileTaskDependsOn, kotlinWarningBaselineGeneratorService.get())
        }

        kotlinWarningBaselineGeneratorService.get().warningFilePaths[project.name] =
            warningFile.absolutePath
        kotlinWarningBaselineGeneratorService.get().baselineFilePaths[project.name] =
            baselineFile.absolutePath
    }

    kotlinWarningBaselineGeneratorService.get().isWriteTask =
        gradle.startParameter.taskNames.any { it.contains(WRITE_TASK_NAME) }
    kotlinWarningBaselineGeneratorService.get().isCheckTask =
        gradle.startParameter.taskNames.any { it.contains(CHECK_TASK_NAME) }

    configureTasks(
        variant = variant,
        target = target,
        baselineFile = baselineFile,
        kotlinWarningBaselineGeneratorService = kotlinWarningBaselineGeneratorService,
        warningFileCollector = warningFileCollector,
        warningFile = warningFile,
        compileTaskDependsOn = compileTaskDependsOn,
    )
}

private fun getVariantFromStartTask(taskNames: List<String>): String {
    return supportedVariantsAndroidTarget.firstOrNull {
        taskNames.contains(it)
    } ?: DEFAULT_VARIANT_ANDROID_TARGET
}

private fun Project.addCompileTaskToService(
    compileTaskDependsOn: Set<String>,
    service: KotlinWarningBaselineGeneratorService,
    variantToDependOn: String,
    skipValidation: Boolean,
) {
    if (compileTaskDependsOn.all {
            // android test hard coded to debug, so it'll never match variant
            it.contains(variantToDependOn, ignoreCase = true) || it.contains(
                "AndroidTest",
                ignoreCase = true,
            )
        }.not() && skipValidation.not()
    ) {
        // if we are running releaseWrite, we don't care about any tasks that are other variants
        return
    }
    val current = service.tasksToComplete[this.name]
    if (current == null) {
        service.tasksToComplete[this.name] = compileTaskDependsOn
    } else {
        service.tasksToComplete[this.name] = current + compileTaskDependsOn
    }
}

private fun taskNameFromCompileTaskSource(
    kotlinCompileTask: KotlinCompileTask?,
    variant: String,
): String {
    return when (kotlinCompileTask) {
        KotlinCompileTask.UNIT_TEST -> "compile${variant.capitalizeCompat()}UnitTestKotlin"
        KotlinCompileTask.MAIN -> "compile${variant.capitalizeCompat()}Kotlin"
        // there is no release for AndroidTest, so hard code to debug
        KotlinCompileTask.ANDROID_TEST -> "compileDebugAndroidTestKotlin"
        null -> throw IllegalArgumentException("Null Kotlin Compile Task Configuration")
    }
}

private fun Project.registerRemoveTask() {
    tasks.create<RemoveKotlinWarningBaselineTask>("removeKotlinWarningBaseline") {
        group = "verification"
        description = "Remove all warning baseline files."
        outputs.upToDateWhen { false }
        this.projectDirectory.set(project.layout.projectDirectory)
        this.buildDirectoryPath.set(
            project.layout.buildDirectory.dir("kotlin-warning").get().asFile.absolutePath,
        )
        this.baselineFilePrefix.set(WARNING_BASELINE_FILE_PREFIX)
    }
}

/** Configures write + check tasks */
private fun Project.configureTasks(
    variant: String,
    target: String,
    baselineFile: File,
    kotlinWarningBaselineGeneratorService: Provider<KotlinWarningBaselineGeneratorService>,
    warningFileCollector: WarningFileCollector,
    warningFile: File,
    compileTaskDependsOn: Set<String>,
) {
    if (kotlinWarningBaselineGeneratorService.get().tasksToComplete[this.name] != null) {
        // only add listener if we have tasks to complete
        val buildOperationExecutor = project.gradle.serviceOf<BuildOperationListenerManager>()
        buildOperationExecutor.addListener(warningFileCollector.buildOperationListener)
    }

    val isWriteTask = kotlinWarningBaselineGeneratorService.get().isWriteTask
    val isCheckTask = kotlinWarningBaselineGeneratorService.get().isCheckTask

    if (isWriteTask) {
        warningFileCollector.fileToWriteTo = baselineFile
    } else if (isCheckTask) {
        warningFile.parentFile.mkdirs()
        warningFileCollector.fileToWriteTo = warningFile
    }

    kotlinWarningBaselineGeneratorService.get().warningFileCollectors[project.name] =
        warningFileCollector
    kotlinWarningBaselineGeneratorService.get().listeners[project.name] =
        warningFileCollector.buildOperationListener

    configureKotlinCompile(compileTaskDependsOn)

    val checkTaskName =
        if (project.isMultiplatformProject()) {
            "${target}${variant.capitalizeCompat()}$CHECK_TASK_NAME"
        } else {
            "${variant}$CHECK_TASK_NAME"
        }

    // register check task
    val check =
        tasks.register<CheckKotlinWarningBaselineTask>(checkTaskName) {
            group = "verification"
            description = "Check that all warnings are in warning baseline files."

            if (baselineFile.exists()) {
                this.baselineFile.set(baselineFile)
            }

            this.warningFilePath.set(warningFile.absolutePath)
            this.kotlinSourceSets.set(project.getKotlinSources())
        }

    // make task depend on compile kotlin task
    check.configure(
        object : Action<CheckKotlinWarningBaselineTask> {
            override fun execute(t: CheckKotlinWarningBaselineTask) {
                kotlinWarningBaselineGeneratorService.get().tasksToComplete[project.name]?.let { tasks ->
                    tasks.forEach {
                        t.dependsOn(project.tasks.named(it))
                    }
                }
            }
        },
    )

    val writeTaskName =
        if (project.isMultiplatformProject()) {
            "${target}${variant.capitalizeCompat()}$WRITE_TASK_NAME"
        } else {
            "${variant}$WRITE_TASK_NAME"
        }

    // register write task
    val write =
        tasks.register<WriteKotlinWarningBaselineTask>(writeTaskName) {
            group = "verification"
            description = "Create or update warning baseline files for the release variant."

            this.kotlinSourceSets.set(project.getKotlinSources())
        }

    // make task depend on compile kotlin task
    write.configure(
        object : Action<WriteKotlinWarningBaselineTask> {
            override fun execute(t: WriteKotlinWarningBaselineTask) {
                kotlinWarningBaselineGeneratorService.get().tasksToComplete[project.name]?.let { tasks ->
                    tasks.forEach {
                        t.dependsOn(project.tasks.named(it))
                    }
                }
            }
        },
    )
}

/**
 * Configure the kotlin compile task that we depend on to have some special behavior when being
 * started from our write and check task
 */
private fun Project.configureKotlinCompile(compileTaskDependsOn: Set<String>) {
    project.tasks
        .withType(KotlinCompile::class.java)
        .configureEach(
            object : Action<KotlinCompile<*>> {
                override fun execute(t: KotlinCompile<*>) {
                    val isWriteTask =
                        gradle.startParameter.taskNames.any { it.contains(WRITE_TASK_NAME) }
                    val isCheckTask =
                        gradle.startParameter.taskNames.any { it.contains(CHECK_TASK_NAME) }

                    // only remove once they've all been completed
                    if ((isWriteTask || isCheckTask) && compileTaskDependsOn.contains(t.name)) {
                        // since we always need the kotlin compile task to fully run, so we get the full output,
                        // we can't do any incremental compilation
                        t.outputs.upToDateWhen { false }
                        project.extensions.extraProperties["kotlin.incremental"] = "false"
                    }
                }
            },
        )
}

/** Checks for main source sets that aren't empty */
private fun Project.hasNonEmptyKotlinSourceSets(): Boolean {
    val kotlinSourceSets =
        extensions
            .getByType(KotlinProjectExtension::class.java)
            .sourceSets
            .toSet()
            .firstOrNull { it.name == "main" }
            ?.kotlin
            ?.srcDirs
            ?: emptySet()

    return kotlinSourceSets.any { file ->
        try {
            Files.walk(file.toPath()).anyMatch { it.pathString.endsWith(".kt") }
        } catch (e: IOException) {
            false
        }
    }
}

private fun Project.getKotlinSources(): List<File> {
    val kotlinSourceSet = extensions.getByType(KotlinProjectExtension::class.java).sourceSets
    return kotlinSourceSet.flatMap {
        it.kotlin
    }
}

private fun warningBaselineFileName(variant: String, target: String): String {
    return WARNING_BASELINE_FILE_PREFIX.plus(
        if (variant.isNotEmpty()) {
            "-$variant"
        } else {
            ""
        }.plus(
            if (target.isNotEmpty()) {
                "-$target"
            } else {
                ""
            },
        ).plus(".txt"),
    )
}

private fun String.capitalizeCompat(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun Project.addTaskToService(
    compileTaskDependsOn: Set<String>,
    kotlinWarningBaselineGeneratorService: KotlinWarningBaselineGeneratorService,
) {
    val current = kotlinWarningBaselineGeneratorService.tasksToComplete[this.name]
    if (current == null) {
        kotlinWarningBaselineGeneratorService.tasksToComplete[this.name] = compileTaskDependsOn
    } else {
        kotlinWarningBaselineGeneratorService.tasksToComplete[this.name] =
            current + compileTaskDependsOn
    }
}
