plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "com.sandwich"
version = "0.1.0"

application {
    mainClass.set("com.sandwich.AppKt")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)

    // JSON
    implementation(libs.ktor.serialization.json)

    // Coroutines
    implementation(libs.coroutines.core)

    // MongoDB
    implementation(libs.mongodb.driver)
    implementation(libs.mongodb.bson.kotlinx)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.junit)
}

tasks.test {
    useJUnitPlatform()
}
