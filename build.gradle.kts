// Always set a default version first
version = "1.0.0-SNAPSHOT"  // Default version

// For local builds, use 0-SNAPSHOT. For CI builds, use the build number from CircleCI
// If a specific version is provided (e.g., from JitPack), use that instead
val providedVersion = findProperty("version") as? String
val buildNumber = findProperty("buildNumber") as? String

// Override default version if parameters are provided
if (providedVersion != null && providedVersion.isNotEmpty()) {
    version = providedVersion
} else if (buildNumber != null && buildNumber.isNotEmpty()) {
    version = "1.0.$buildNumber"
}

// Always ensure we have a valid group ID
val providedGroup = findProperty("group") as? String
group = if (providedGroup.isNullOrBlank()) "com.github.incept5" else providedGroup

// Log the group and version for debugging
println("Building with group: $group")
println("Building with version: $version")

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    // publish to maven repositories
    `maven-publish`
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

// Configure Kotlin to target JVM 17
kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Explicitly set groupId, artifactId, and version
            groupId = project.group.toString()
            artifactId = "error-core"
            version = project.version.toString()

            from(components["java"])

            // POM information is automatically included with sources and javadoc
            pom {
                name.set("Error Core ")
                description.set("Core functionality for Error Handling in Rest Services")
                url.set("https://github.com/incept5/error-core")

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

    // Configure local Maven repository for local builds
    repositories {
        mavenLocal()
    }
}

// For JitPack compatibility
tasks.register("install") {
    dependsOn(tasks.named("publishToMavenLocal"))
}

// Always publish to local Maven repository after build for local development
tasks.named("build") {
    finalizedBy(tasks.named("publishToMavenLocal"))
}
