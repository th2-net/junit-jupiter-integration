import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compatibility.validator)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.th2.publish)
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
}

ktlint {
    version.set("0.50.0")
    reporters {
        reporter(ReporterType.HTML)
    }
}
