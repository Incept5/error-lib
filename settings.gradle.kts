rootProject.name = "error-lib"

include("error-core")
include("error-quarkus")
include("error-quarkus-sample")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { 
            url = uri("https://jitpack.io") 
            content {
                includeGroup("com.github.incept5")
            }
        }
    }
}

