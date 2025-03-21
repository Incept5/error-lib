plugins {
    `java-library`
    `maven-publish`

    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    // include quarkus metadata in the jar
    alias(libs.plugins.jandex)
}

dependencies {
    implementation(platform(libs.quarkus.bom))

    api(project(":error-core"))
    api("io.vertx:vertx-core")
    api("io.quarkus.resteasy.reactive:resteasy-reactive")
    api("jakarta.enterprise:jakarta.enterprise.cdi-api")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.ws.rs:jakarta.ws.rs-api")

    implementation(libs.incept5.correlation)
    implementation("io.quarkus:quarkus-kotlin")
    runtimeOnly("io.quarkus.resteasy.reactive:resteasy-reactive-common")
    runtimeOnly("io.quarkus:quarkus-resteasy-reactive")

    testImplementation(platform(libs.kotest.bom))
    testRuntimeOnly("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-shared")
    testImplementation("io.kotest:kotest-framework-api")
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.dsl)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}

tasks.test {
    dependsOn(tasks.jandex)
}
