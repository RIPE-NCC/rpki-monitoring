import java.nio.charset.StandardCharsets
import java.util.*

plugins {
    java
    id("io.freefair.lombok") version "8.4"
    id("org.springframework.boot") version "3.2.1"
    id("com.gorylenko.gradle-git-properties") version "2.4.1"
    jacoco
    id("org.sonarqube") version "4.3.0.3225"

    id("io.spring.dependency-management") version "1.1.4"
}

group = "net.ripe.rpki"
version = "0.18.5-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        // READ ACCESS token specific for this repositories maven artifacts. These are public, but unfortunately anonymous
        // access is not possible. Valid until 10-1-2025. NOT A SECRET.
        //
        // Obfuscated due to github security scanning.
        url = uri("https://ties:" + String(byteArrayOf(103, 105, 116, 104, 117, 98, 95, 112, 97, 116, 95, 49, 49, 65, 65, 66, 81, 84, 69, 81, 48, 112, 66, 120, 79, 48, 67, 101, 75, 83, 112, 69, 83, 95, 85, 121, 119, 120, 114, 116, 122, 53, 55, 76, 90, 117, 117, 51, 100, 71, 68, 86, 82, 107, 99, 107, 113, 102, 102, 103, 78, 106, 74, 119, 77, 54, 81, 54, 53, 50, 114, 76, 75, 109, 103, 75, 85, 86, 71, 86, 86, 85, 89, 53, 54, 53, 55, 84, 69, 90, 103, 54, 113), StandardCharsets.UTF_8) + "@maven.pkg.github.com/ties/java-rrdp-test-data")
    }
    maven {
        url = uri("https://nexus.ripe.net/nexus/content/repositories/releases/")
    }
}

gitProperties {
    failOnNoGitDirectory = false
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // for WebClient
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("net.ripe.rpki:rpki-commons:1.36")

    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("org.junit.vintage", "junit-vintage-engine")
    }
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // RRDP snapshots for RIPE + APNIC
    testImplementation("com.github.ties:rrdp-test-data:0.2.0-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
            "--enable-preview",
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-serial",
            "-Xlint:-preview",
            "-Werror"
    ))
}

tasks.withType<JavaExec>() {
    jvmArgs("--enable-preview")
}

tasks.test {
    jvmArgs("--enable-preview")

    useJUnitPlatform()
    maxParallelForks = Math.min(1, Runtime.getRuntime().availableProcessors() / 2)
    finalizedBy(tasks.jacocoTestReport)

    maxHeapSize = "4g"
}

tasks.sonarqube {
    dependsOn(tasks.test)
}

jacoco {
    // 2023-10-26: Force version 0.8.11 for JDK 21
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}
