plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.quarkus)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))

    api("jakarta.enterprise:jakarta.enterprise.cdi-api")
    api("jakarta.validation:jakarta.validation-api")
    api("jakarta.ws.rs:jakarta.ws.rs-api")

    implementation("io.quarkus:quarkus-core")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation(project(":error-core"))

    runtimeOnly(project(":error-quarkus"))
    runtimeOnly("io.quarkus:quarkus-resteasy-reactive-jackson")
    runtimeOnly("io.quarkus:quarkus-hibernate-validator")
    runtimeOnly("io.quarkus:quarkus-arc")
    runtimeOnly("io.quarkus:quarkus-resteasy-reactive")

    testImplementation("io.quarkus:quarkus-test-common")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation(platform(libs.mockito.bom))
    testImplementation("org.mockito:mockito-core")
    testImplementation(libs.mockito.kotlin)

    testRuntimeOnly("io.quarkus:quarkus-junit5-mockito")
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}
