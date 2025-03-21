plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    // publish to nexus
    `maven-publish`
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
}

dependencies {

    // Direct JitPack dependency instead of using the version catalog
    implementation("com.github.incept5:json-lib:master-SNAPSHOT")

    // jackson
    api(platform(libs.jackson.bom))
    api("com.fasterxml.jackson.core:jackson-annotations")

    // test dependencies
    // kotest
    testImplementation(enforcedPlatform(libs.kotest.bom))
    testImplementation("io.kotest:kotest-assertions-core")
    testImplementation("io.kotest:kotest-assertions-shared")
    testImplementation("io.kotest:kotest-framework-api")
    testImplementation("io.kotest:kotest-framework-datatest")
    testRuntimeOnly("io.kotest:kotest-runner-junit5")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
