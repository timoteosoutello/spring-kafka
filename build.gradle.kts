plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.soutelloit"
version = "0.0.1-SNAPSHOT"
description = "Simple Spring Boot 4 + Kafka consumer sample"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

repositories {
    mavenCentral()
}

// The Spring Boot plugin already adds this, but declaring it explicitly keeps IDEs
// that read the Gradle model (and anyone running javac directly) honest: without it,
// Spring cannot infer @RequestParam / @PathVariable names from method parameters.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.jackson)

    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.kafka.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
