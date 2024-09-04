# Kotlin Warning Baseline Generator

A gradle plugin for detecting when new Kotlin warnings are added.

This is spiritually similar to `android.kotlinOptions.allWarningsAsError` or `-Werror` in your Kotlin / Android project.
If you can or already do use those, this plugin isn't of much value to you.
The value of this plugin is to people who can't enable those settings due to legacy modules having too many errors to fix at once.
The Gradle tasks added will allow you to generate, check, and delete kotlin warning baselines. 

1. Generate

Generating the baseline for your project can be done via `./gradlew <VARIANT>WriteKotlinWarningBaseline` (example: `./gradlew releaseWriteKotlinWarningBaseline`)
You can also generate the baseline for a specific module via `./gradlew :<MODULE>:<VARIANT>WriteKotlinWarningBaseline` (example: `./gradlew :app:releaseWriteKotlinWarningBaseline`)

1. Check

Checking the current code against the baseline can be done via `./gradlew <VARIANT>CheckKotlinWarningBaseline` (example: `./gradlew releaseCheckKotlinWarningBaseline`)
You can also check the baseline for a specific module via `./gradlew :<MODULE>:<VARIANT>CheckKotlinWarningBaseline` (example: `./gradlew :app:releaseCheckKotlinWarningBaseline`))


1. Remove

Baselines can be deleted via `./gradlew removeKotlinWarningBaseline`
Baseliens for a specific module can be done via `./gradlew :<MODULE>:removeKotlinWarningBaseline`

## Adding To Your Project

In your root build file:

```kotlin
plugins {
    id("com.joetr.kotlin.warning.baseline.generator") version "<latest version>" apply false
}
```

In any module you want to apply checks:

```kotlin
plugins {
    id("com.joetr.kotlin.warning.baseline.generator")
}
```

## Configuration

Right now, warnings can be observed from main or test source sets and is configurable via the following DSL in your `build.gradle.kts`:

```
kotlinWarningBaselineGenerator {
    // default is set to just KotlinCompileTask.MAIN
    kotlinCompileTasksToConfigure.set(
        listOf(
            KotlinCompileTask.MAIN,
            KotlinCompileTask.UNIT_TEST,
        )
    )
}
```

## Multiplatform

Multiplatform support is a WIP, but not ready yet. 