/*
 * Copyright 2021-2024 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Versions.Common
import Versions.Kotlin
import Versions.Test

plugins {
    kotlin("jvm")
}

dependencies {

    implementation(kotlin("stdlib", Kotlin.version))
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Kotlin.version)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Kotlin.coroutinesVersion)
    implementation("io.vavr", "vavr", Common.vavrVersion)

    testImplementation("io.kotest", "kotest-runner-junit5", Test.kotestRunnerVersion)
    testImplementation("io.mockk", "mockk", Test.mockkVersion)
}

// apply(from = "../publishing.gradle.kts")