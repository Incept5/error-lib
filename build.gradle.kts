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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure Kotlin to target JVM 21
kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Apply common configuration to all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    
    group = rootProject.group
    version = rootProject.version
    
    java {
        withJavadocJar()
        withSourcesJar()
    }
    
    // Configure Kotlin to target JVM 21
    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    // Configure publishing for all subprojects
    configure<PublishingExtension> {
        repositories {
            mavenLocal()
        }
    }
}

