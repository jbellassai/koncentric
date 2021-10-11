/*
 * Copyright 2021 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
    id("nu.studer.jooq") version Versions.Plugins.jooqPluginVersion
}

dependencies {
    implementation(kotlin("stdlib", Kotlin.version))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Kotlin.coroutinesVersion)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactor", Kotlin.coroutinesVersion)
    implementation("org.jooq", "jooq", Common.jooqVersion)
    implementation(project(":koncentric-core"))

    testImplementation("io.kotest", "kotest-runner-junit5", Test.kotestRunnerVersion)
    testImplementation("io.mockk", "mockk", Test.mockkVersion)
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:${Test.embeddedPostgresVersion}"))
    testImplementation("io.zonky.test", "embedded-postgres", Test.zonkyVersion)
    testImplementation("org.slf4j", "slf4j-simple", Test.slf4jSimpleVersion)

    testImplementation(project(":test-domains:users-and-groups"))

    jooqGenerator("org.jooq", "jooq-meta-extensions", version = Common.jooqVersion)
}

jooq {
    version.set(Common.jooqVersion)
    configurations {
        create("test") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.DEBUG
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties = listOf(
                            org.jooq.meta.jaxb.Property()
                                .withKey("scripts")
                                .withValue("../test-domains/users-and-groups/src/main/resources/io/koncentric/test_domains/users_and_groups/schema/postgresql/V1__initial_schema.sql")
                        )
                    }
                    generate.apply {
                        withJavaTimeTypes(true)
                    }
                    target.apply {
                        packageName = "io.koncentric.persistence.jooq.user_and_group.generated"
                    }
                }
            }

        }
    }
}

apply(from = "../publishing.gradle.kts")