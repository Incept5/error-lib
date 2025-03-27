// Set default version and group
group = "com.github.incept5"  // Default group

// Determine the version to use
val providedVersion = project.properties["version"]?.toString()
val buildNumber = project.properties["buildNumber"]?.toString()

// Set the version based on the available information
if (providedVersion != null && providedVersion != "unspecified" && providedVersion != "1.0.0-SNAPSHOT") {
    version = providedVersion
    println("Using explicitly provided version: $providedVersion")
} else if (buildNumber != null && buildNumber.isNotEmpty()) {
    version = "1.0.$buildNumber"
    println("Using build number version: 1.0.$buildNumber")
} else {
    version = "1.0.0-SNAPSHOT"
    println("Using default version: 1.0.0-SNAPSHOT")
}

// If a specific group is provided, use that
val providedGroup = project.properties["group"]?.toString()
if (providedGroup != null && providedGroup.isNotEmpty()) {
    group = providedGroup
    println("Using provided group: $providedGroup")
} else {
    println("Using default group: $group")
}

// Print all project properties for debugging
println("All project properties:")
project.properties.forEach { (key, value) ->
    if (key.contains("version") || key.contains("group") || key.contains("buildNumber")) {
        println("  $key = $value")
    }
}

// Print the final version and group
println("Final version: $version")
println("Final group: $group")

// Log the final group and version for debugging
println("Building with group: $group")
println("Building with version: $version")

// Additional debug information
println("System properties:")
System.getProperties().forEach { key, value ->
    if (key.toString().contains("version") || key.toString().contains("group")) {
        println("  $key = $value")
    }
}

println("Gradle properties:")
project.properties.forEach { key, value ->
    if (key.toString().contains("version") || key.toString().contains("group")) {
        println("  $key = $value")
    }
}

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
    
    // Log subproject configuration
    println("Configuring subproject: ${project.name}")
    println("  Group: ${project.group}")
    println("  Version: ${project.version}")
    
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
        
        // Ensure all publications use the correct group and version
        publications.withType<MavenPublication>().configureEach {
            groupId = project.group.toString()
            version = project.version.toString()
            
            println("Configured publication for ${project.name}:")
            println("  GroupId: $groupId")
            println("  ArtifactId: $artifactId")
            println("  Version: $version")
        }
    }
    
    // Add a task to verify the publication
    tasks.register("verifyPublication") {
        dependsOn("publishToMavenLocal")
        doLast {
            val projectVersion = project.version.toString()
            val projectGroup = project.group.toString()
            val artifactId = project.name
            val expectedPath = "${System.getProperty("user.home")}/.m2/repository/${projectGroup.replace(".", "/")}/$artifactId/$projectVersion/$artifactId-$projectVersion.jar"
            
            println("Verifying publication for ${project.name}")
            println("  Expected JAR path: $expectedPath")
            
            val jarFile = file(expectedPath)
            if (jarFile.exists()) {
                println("  ✅ JAR file exists at expected path")
            } else {
                println("  ❌ JAR file does not exist at expected path")
                println("  Searching for JAR file...")
                val homeDir = System.getProperty("user.home")
                val jarFiles = fileTree("$homeDir/.m2/repository").matching {
                    include("**/$artifactId-$projectVersion.jar")
                }
                jarFiles.forEach { file ->
                    println("  Found JAR at: ${file.absolutePath}")
                }
            }
        }
    }
    
    // Always verify publication after publishing to Maven local
    tasks.named("publishToMavenLocal") {
        finalizedBy("verifyPublication")
    }
}

// Add a task to publish only the modules we want to JitPack
tasks.register("publishJitPackModules") {
    dependsOn(":error-core:publishToMavenLocal", ":error-quarkus:publishToMavenLocal")
    doLast {
        println("Published JitPack modules:")
        println("  com.github.incept5:error-core:${project.version}")
        println("  com.github.incept5:error-quarkus:${project.version}")
    }
}

// Skip publishing the root project
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        project != rootProject
    }
}

tasks.withType<PublishToMavenLocal>().configureEach {
    onlyIf {
        project != rootProject
    }
}

// Explicitly disable publishing for the root project
publishing {
    publications.configureEach {
        // This will effectively disable the publication
        // by making it impossible to resolve
        if (this is MavenPublication) {
            artifactId = "DO-NOT-PUBLISH"
            version = "DISABLED"
        }
    }
}

