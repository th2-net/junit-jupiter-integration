import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.net.URL

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compatibility.validator)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.publish)
    alias(libs.plugins.git)
    alias(libs.plugins.dependencycheck)
    alias(libs.plugins.licenses)
    alias(libs.plugins.download)
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
    api(libs.th2.common)
    implementation(libs.th2.cradle.cassandra)
    implementation(libs.th2.grpc.service.generator) {
        because("cannot work with retry configuration for gRPC without that")
    }

    api(platform(libs.testcontainers.bom))
    api("org.testcontainers:rabbitmq") {
        because("integration with rabbitmq")
    }
    api("org.testcontainers:cassandra") {
        because("integration with cradle")
    }
    implementation("com.datastax.oss:java-driver-core") {
        because("use CQL in integration")
    }

    implementation(libs.kotlin.logging)

    implementation(platform(libs.junit.bom))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.junit.platform:junit-platform-commons") {
        because("has methods to simplify the reflection")
    }

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(libs.th2.grpc.check1) {
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
                val urlProvider = provider { findProperty("vcs_url") as? String }
                url.set(urlProvider)

                scm {
                    url.set(urlProvider)
                }

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
    val licenseNormalizerBundlePath = layout.buildDirectory.file("license-normalizer-bundle.json").get().asFile.path

    if (!file(licenseNormalizerBundlePath).exists()) {
        download.run {
            src("https://raw.githubusercontent.com/th2-net/.github/main/license-compliance/gradle-license-report/license-normalizer-bundle.json")
            dest(layout.buildDirectory.file("license-normalizer-bundle.json"))
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
