// Set default version and group
group = "com.github.incept5"  // Default group for local development
// For JitPack, the group will be com.github.incept5

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

// Check for publishGroupId which will be used for Maven publications
val publishGroupId = project.properties["publishGroupId"]?.toString() ?: group.toString()
println("Using publish group ID: $publishGroupId")

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
println("Final publish group ID: $publishGroupId")

// Log the final group and version for debugging
println("Building with group: $group")
println("Building with version: $version")
println("Publishing with group ID: $publishGroupId")

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
    
    // Get the publishGroupId from root project
    val publishGroupId = rootProject.properties["publishGroupId"]?.toString() ?: rootProject.group.toString()
    
    // Log subproject configuration
    println("Configuring subproject: ${project.name}")
    println("  Group: ${project.group}")
    println("  Version: ${project.version}")
    println("  Publish GroupId: $publishGroupId")
    
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
            // For JitPack compatibility, we need to use the correct group ID format
            // JitPack expects: com.github.{username}.{repository}
            val jitpackGroupId = if (System.getenv("JITPACK") != null) {
                // When building on JitPack
                "com.github.incept5.error-lib"
            } else {
                // For local development
                publishGroupId
            }
            
            // Use publishGroupId instead of project.group to ensure correct artifact path
            groupId = jitpackGroupId
            version = project.version.toString()
            
            println("Configured publication for ${project.name}:")
            println("  GroupId: $groupId")
            println("  ArtifactId: $artifactId")
            println("  Version: $version")
            
            // Add SCM information for JitPack
            pom {
                scm {
                    connection.set("scm:git:github.com/incept5/error-lib.git")
                    developerConnection.set("scm:git:ssh://github.com/incept5/error-lib.git")
                    url.set("https://github.com/incept5/error-lib/tree/main")
                }
            }
        }
    }
    
    // Add a task to verify the publication
    tasks.register("verifyPublication") {
        dependsOn("publishToMavenLocal")
        doLast {
            val projectVersion = project.version.toString()
            // Use publishGroupId for verification
            val projectGroup = publishGroupId
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
        val publishGroupId = project.properties["publishGroupId"]?.toString() ?: project.group.toString()
        println("Published JitPack modules:")
        println("  $publishGroupId:error-core:${project.version}")
        println("  $publishGroupId:error-quarkus:${project.version}")
        
        // Verify the artifacts were published to the correct location
        val homeDir = System.getProperty("user.home")
        val expectedCorePath = "$homeDir/.m2/repository/${publishGroupId.replace(".", "/")}/error-core/${project.version}/error-core-${project.version}.jar"
        val expectedQuarkusPath = "$homeDir/.m2/repository/${publishGroupId.replace(".", "/")}/error-quarkus/${project.version}/error-quarkus-${project.version}.jar"
        
        println("Verifying published artifacts:")
        println("  Expected error-core path: $expectedCorePath")
        println("  Expected error-quarkus path: $expectedQuarkusPath")
        
        val coreFile = file(expectedCorePath)
        val quarkusFile = file(expectedQuarkusPath)
        
        if (coreFile.exists()) {
            println("  ✅ error-core JAR exists at expected path")
        } else {
            println("  ❌ error-core JAR does not exist at expected path")
        }
        
        if (quarkusFile.exists()) {
            println("  ✅ error-quarkus JAR exists at expected path")
        } else {
            println("  ❌ error-quarkus JAR does not exist at expected path")
        }
    }
}

// Configure root project publishing for JitPack
publishing {
    publications {
        create<MavenPublication>("mavenRoot") {
            // For JitPack compatibility
            groupId = "com.github.incept5"
            artifactId = "error-lib"
            version = project.version.toString()
            
            // Create an empty JAR for the root project
            artifact(tasks.register("emptyJar", Jar::class) {
                archiveClassifier.set("empty")
            })
            
            // POM information
            pom {
                name.set("Error Library")
                description.set("Error handling library for Quarkus applications")
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
                
                scm {
                    connection.set("scm:git:github.com/incept5/error-lib.git")
                    developerConnection.set("scm:git:ssh://github.com/incept5/error-lib.git")
                    url.set("https://github.com/incept5/error-lib/tree/main")
                }
                
                // Define dependencies on the subprojects
                withXml {
                    val dependencies = asNode().appendNode("dependencies")
                    
                    project(":error-core").let { subproject ->
                        val dependency = dependencies.appendNode("dependency")
                        dependency.appendNode("groupId", "com.github.incept5.error-lib")
                        dependency.appendNode("artifactId", "error-core")
                        dependency.appendNode("version", project.version)
                        dependency.appendNode("scope", "compile")
                    }
                    
                    project(":error-quarkus").let { subproject ->
                        val dependency = dependencies.appendNode("dependency")
                        dependency.appendNode("groupId", "com.github.incept5.error-lib")
                        dependency.appendNode("artifactId", "error-quarkus")
                        dependency.appendNode("version", project.version)
                        dependency.appendNode("scope", "compile")
                    }
                }
            }
        }
    }
    
    repositories {
        mavenLocal()
    }
}


