<div align="center">
  <img src="assets/kwbg_logo.png" width="512">
  <h1>Kotlin Warning Baseline Generator</h1>
</div>

<p align="center">
  <a href="https://central.sonatype.com/namespace/com.joetr.kotlin.warning.baseline.generator"><img alt="Maveb Central" src="https://img.shields.io/maven-central/v/com.joetr.kotlin.warning.baseline.generator/com.joetr.kotlin.warning.baseline.generator.gradle.plugin"/></a>
  <a href="https://opensource.org/license/mit/"><img alt="License" src="https://img.shields.io/badge/License-MIT-blue.svg"/></a>
  <a href="https://github.com/j-roskopf/KotlinWarningBaselineGenerator/actions/workflows/release.yml"><img alt="Release Workflow" src="https://github.com/j-roskopf/KotlinWarningBaselineGenerator/actions/workflows/release.yml/badge.svg"/></a>
  <a href="https://mailchi.mp/kotlinweekly/kotlin-weekly-423"><img alt="Kotlin Weekly" src="https://img.shields.io/badge/News-Kotlin_Weekly_%23423-palevioletred?logo=kotlin"/></a>
  <a href="https://hitsofcode.com/github/j-roskopf/KotlinWarningBaselineGenerator/view?branch=main"><img src="https://hitsofcode.com/github/j-roskopf/KotlinWarningBaselineGenerator?branch=main"/></a>
</p><br>

A gradle plugin for detecting when new Kotlin warnings are added.

This is spiritually similar to `android.kotlinOptions.allWarningsAsError` or `-Werror` in your Kotlin / Android project.
If you can or already do use those, this plugin isn't of much value to you.
The value of this plugin is to people who can't enable those settings due to legacy modules having too many errors to fix at once.
The Gradle tasks added will allow you to generate, check, and delete kotlin warning baselines. 

* Generate

Generating the baseline for your project can be done via `./gradlew <VARIANT>WriteKotlinWarningBaseline` (example: `./gradlew releaseWriteKotlinWarningBaseline`)
You can also generate the baseline for a specific module via `./gradlew :<MODULE>:<VARIANT>WriteKotlinWarningBaseline` (example: `./gradlew :app:releaseWriteKotlinWarningBaseline`)

* Check

Checking the current code against the baseline can be done via `./gradlew <VARIANT>CheckKotlinWarningBaseline` (example: `./gradlew releaseCheckKotlinWarningBaseline`)
You can also check the baseline for a specific module via `./gradlew :<MODULE>:<VARIANT>CheckKotlinWarningBaseline` (example: `./gradlew :app:releaseCheckKotlinWarningBaseline`))


* Remove

Baselines can be deleted via `./gradlew removeKotlinWarningBaseline`
Baselines for a specific module can be done via `./gradlew :<MODULE>:removeKotlinWarningBaseline`

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

Right now, warnings can be observed from main, unit test, or android test source sets and is configurable via the following DSL in your `build.gradle.kts`:

```
kotlinWarningBaselineGenerator {
    // default is set to just KotlinCompileTask.MAIN
    kotlinCompileTasksToConfigure.set(
        listOf(
            KotlinCompileTask.MAIN,
            KotlinCompileTask.UNIT_TEST,
            KotlinCompileTask.ANDROID_TEST,
        )
    )
}
```

Note - Since this plugin requires all kotlin warnings to be output during task execution, incremental compilation + up to date checks for the kotlin compile task are modified when executing the check / generate gradle tasks.

## Multiplatform

In a Multiplatform project, Kotlin Warning Baseline Generator adds the same 2 `Check` and `Generate` tasks (as well as a root `removeKotlinWarningBaseline` task) for each supported target.

### JVM

`./gradlew :desktopApp:jvmWriteKotlinWarningBaseline`
`./gradlew :desktopApp:jvmCheckKotlinWarningBaseline`

### Android

`./gradlew :androidApp:androidReleaseCheckKotlinWarningBaseline`
`./gradlew :androidApp:androidReleaseWriteKotlinWarningBaseline`

### Web

`./gradlew :webApp:jsWriteKotlinWarningBaseline`
`./gradlew :webApp:jsCheckKotlinWarningBaseline`

### iOS

WIP

![](https://img.shields.io/badge/Android-black.svg?style=for-the-badge&logo=android) | ![](https://img.shields.io/badge/iOS-black.svg?style=for-the-badge&logo=apple) | ![](https://img.shields.io/badge/Desktop-black.svg?style=for-the-badge&logo=apple) | ![](https://img.shields.io/badge/Web-black.svg?style=for-the-badge&logo=google-chrome)
:----: | :----: |:----------------------------------------------------------------------------------:| :----:
✅ | ❌ |                                         ✅                                          | ✅

## Signing locally

This is required to test.

In memory GPG key - https://vanniktech.github.io/gradle-maven-publish-plugin/central/#in-memory-gpg-key

* Install gnupg - https://formulae.brew.sh/formula/gnupg
* Generate a key - https://central.sonatype.org/publish/requirements/gpg/#generating-a-key-pair
  * `gpg --full-generate-key` 
* List keys and grab newly generated key (40 digits)
  * `gpg --list-keys`
* `gpg --export-secret-keys THE_KEY_THAT_YOU_JUST_GENERATED > kotlinwarningbaselinegenerator.gpg`
* Modify your gradle home `gradle.properties` with the following:
```
signing.keyId=LAST_8_DIGITS_OF_KEY
signing.password=PASSWORD_USED_TO_GENERATE_KEY
signing.secretKeyRingFile=/Users/YOURUSERNAME/.gnupg/kotlinwarningbaselinegenerator.gpg (or wherever you stored the keyring you generated earlier)
```

## Prior Art

https://github.com/Doist/kotlin-warning-baseline - [Relation](https://github.com/j-roskopf/KotlinWarningBaselineGenerator/issues/3)
