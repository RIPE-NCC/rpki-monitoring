import java.nio.charset.StandardCharsets
import java.util.*

plugins {
    java
    id("io.freefair.lombok") version "8.4"
    id("org.springframework.boot") version "3.2.1"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    jacoco
    id("org.sonarqube") version "6.0.1.5171"

    id("io.spring.dependency-management") version "1.1.7"
}

group = "net.ripe.rpki"
version = "0.20.1-SNAPSHOT"

repositories {
    mavenCentral()
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

    implementation("net.ripe.rpki:rpki-commons:1.39.1")

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
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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

sourceSets {
    create("integration") {
        java.srcDir("src/integration/java")
        resources.srcDir("src/integration/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

configurations {
    val integrationImplementation by getting {
        extendsFrom(testImplementation.get())
    }
    val integrationRuntimeOnly by getting {
        extendsFrom(testRuntimeOnly.get())
    }
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")

    useJUnitPlatform()
    maxParallelForks = Math.min(1, Runtime.getRuntime().availableProcessors() / 2)
    finalizedBy(tasks.jacocoTestReport)

    maxHeapSize = "4g"
}

tasks.register<Test> ("integrationTest") {
     description = "Run system integration tests. Requires network access.";
     group = "verification"
     testClassesDirs = sourceSets["integration"].output.classesDirs
     classpath = sourceSets["integration"].runtimeClasspath
     mustRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(tasks.named("integrationTest"))
}

tasks.sonarqube {
    dependsOn(tasks.test)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}
