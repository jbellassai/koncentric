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

apply(plugin = "java-library")
apply(plugin = "org.jetbrains.dokka")
apply(plugin = "maven-publish")
apply(plugin = "signing")

fun Project.java(action: JavaPluginExtension.() -> Unit) =
    configure(action)

fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)

fun Project.signing(configure: SigningExtension.() -> Unit): Unit =
    configure(configure)

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
}

val dokka = tasks.named("dokkaJavadoc")
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(dokka)
}

publishing {
    repositories {
        maven {
            val ossrhUsername: String by project
            val ossrhPassword: String by project
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            name = "deploy"
            url = if (Ci.isRelease) releasesRepoUrl else snapshotsRepoUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: ossrhUsername
                password = System.getenv("OSSRH_PASSWORD") ?: ossrhPassword
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(dokkaJar)
            pom {
                name.set("Koncentric")
                description.set("A lightweight framework for domain-centric persistence in Kotlin")
                url.set("https://github.com/jbellassai/koncentric")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("jbellassai")
                        name.set("John Bellassai")
                        url.set("https://github.com/jbellassai")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com//jbellassai/koncentric.git")
                    developerConnection.set("scm:git:ssh://github.com:jbellassai/koncentric.git")
                    url.set("https://github.com/jbellassai/koncentric")
                }
            }
        }
    }
}

val publications = (extensions.getByName("publishing") as PublishingExtension).publications

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKeyId != null && signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    }
    sign(publications)
}