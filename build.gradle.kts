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

plugins {
    kotlin("jvm") version Versions.Kotlin.version apply false
    id("org.jetbrains.dokka") version Versions.Kotlin.dokkaVersion apply false
}

allprojects {
    group = "io.github.jbellassai.koncentric"
    version = "0.2.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = JVM.target
    }

    tasks.withType<JavaCompile> {
        targetCompatibility = JVM.target
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}