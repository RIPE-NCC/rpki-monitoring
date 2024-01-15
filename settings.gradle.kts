rootProject.name = "rpki-monitoring"

plugins {
    // Toolchain resolver allows auto-download of JDK 21 which is not in container
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.7.0")
}
