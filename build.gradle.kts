plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.21"
}

group = "com.codewithfk"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")

}

repositories {
    mavenCentral()
}

dependencies {

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.jetbrains.exposed:exposed-java-time:0.39.1") // For datetime support
    implementation("mysql:mysql-connector-java:8.0.33")
    // Ktor Core
    implementation("io.ktor:ktor-server-core-jvm:2.3.0")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Ktor Features
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.0")
    implementation("io.ktor:ktor-server-config-yaml-jvm:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.postgresql:postgresql:42.5.1")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Stripe
    implementation("com.stripe:stripe-java:22.21.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // Google Maps
    implementation("com.google.maps:google-maps-services:2.1.2")

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")

    implementation("io.ktor:ktor-server-websockets:1.6.0")

    // Ktor client
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.0")

}
