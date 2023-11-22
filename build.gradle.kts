import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.net.URL

plugins {
    kotlin("jvm") version "1.8.22"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("org.owasp.dependencycheck") version "8.4.0"
    id("com.gorylenko.gradle-git-properties") version "2.4.1"
    id("com.github.jk1.dependency-license-report") version "2.5"
    id("de.undercouch.download") version "5.4.0"

    `maven-publish`
    signing
}

group = "com.exactpro.th2"

@Suppress("PropertyName")
val release_version: String by project
version = release_version

repositories {
    mavenCentral()
}

java {
    // to comply with sonatype requirements
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api("com.exactpro.th2:common:5.7.1-dev")
    implementation("com.exactpro.th2:cradle-cassandra:5.1.4-dev")
    implementation("com.exactpro.th2:grpc-service-generator:3.4.0") {
        because("cannot work with retry configuraiton for gRPC without that")
    }

    api(platform("org.testcontainers:testcontainers-bom:1.19.0"))
    api("org.testcontainers:rabbitmq") {
        because("integration with rabbitmq")
    }
    api("org.testcontainers:cassandra") {
        because("integration with cradle")
    }
    implementation("com.datastax.oss:java-driver-core") {
        because("use CQL in integration")
    }

    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation(platform("org.junit:junit-bom:5.10.0"))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.junit.platform:junit-platform-commons") {
        because("has methods to simplify the reflection")
    }

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("com.exactpro.th2:grpc-check1:4.2.0-dev") {
        because("test gRPC integration")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

ktlint {
    version.set("0.50.0")
    reporters {
        reporter(ReporterType.HTML)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(project.components["java"])

            pom {
                name.set(project.name)
                packaging = "jar"
                description.set(project.description)
                url.set("")

                scm {
                    url.set("")
                }

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("developer")
                        name.set("developer")
                        email.set("developer@exactpro.com")
                    }
                }
            }
        }
    }
    repositories {
        // Nexus repo to publish from gitlab
        maven {
            name = "nexusRepository"
            credentials {
                username = project.findProperty("nexus_user")?.toString()
                password = project.findProperty("nexus_password")?.toString()
            }
            url = project.findProperty("nexus_url").run { uri(toString()) }
        }
    }
}

nexusPublishing {
    this.repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

signing {
    val signingKey = findProperty("signingKey")?.toString()
    val signingPassword = findProperty("signingPassword")?.toString()
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

// conditionals for publications
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        (
            repository == publishing.repositories["nexusRepository"] &&
                project.hasProperty("nexus_user") &&
                project.hasProperty("nexus_password") &&
                project.hasProperty("nexus_url")
            ) ||
            (
                repository == publishing.repositories["sonatype"] &&
                    project.hasProperty("sonatypeUsername") &&
                    project.hasProperty("sonatypePassword")
                )
    }
}
tasks.withType<Sign>().configureEach {
    onlyIf {
        project.hasProperty("signingKey") &&
            project.hasProperty("signingPassword")
    }
}
// disable running task 'initializeSonatypeStagingRepository' on a gitlab
tasks.configureEach {
    if (this.name == "initializeSonatypeStagingRepository" &&
        !(project.hasProperty("sonatypeUsername") && project.hasProperty("sonatypePassword"))
    ) {
        this.enabled = false
    }
}

dependencyCheck {
    formats = listOf("SARIF", "JSON", "HTML")
    failBuildOnCVSS = 5.0f
    analyzers.apply {
        assemblyEnabled = false
        nugetconfEnabled = false
        nodeEnabled = false
    }
}

licenseReport {
    val licenseNormalizerBundlePath = "$buildDir/license-normalizer-bundle.json"

    if (!file(licenseNormalizerBundlePath).exists()) {
        download.run {
            src("https://raw.githubusercontent.com/th2-net/.github/main/license-compliance/gradle-license-report/license-normalizer-bundle.json")
            dest("$buildDir/license-normalizer-bundle.json")
            overwrite(false)
        }
    }

    filters = arrayOf(
        LicenseBundleNormalizer(licenseNormalizerBundlePath, false),
    )
    renderers = arrayOf(
        JsonReportRenderer("licenses.json", false),
    )
    excludeOwnGroup = false
    allowedLicensesFile = URL("https://raw.githubusercontent.com/th2-net/.github/main/license-compliance/gradle-license-report/allowed-licenses.json")
}
