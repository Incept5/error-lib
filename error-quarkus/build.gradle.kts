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
    api("io.quarkus:quarkus-resteasy-reactive")
    api("jakarta.enterprise:jakarta.enterprise.cdi-api")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.ws.rs:jakarta.ws.rs-api")

    implementation(libs.incept5.correlation)
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    runtimeOnly("io.quarkus:quarkus-arc")

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
            groupId = project.group.toString()
            artifactId = "error-quarkus"
            version = project.version.toString()
            
            from(components["java"])
            
            // POM information
            pom {
                name.set("Error Quarkus")
                description.set("Quarkus integration for Error Handling in Rest Services")
                url.set("https://github.com/incept5/error-lib")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("incept5")
                        name.set("Incept5")
                        email.set("info@incept5.com")
                    }
                }
            }
        }
    }
}

tasks.test {
    dependsOn(tasks.jandex)
    useJUnitPlatform()
}
