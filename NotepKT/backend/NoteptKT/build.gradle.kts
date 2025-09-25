plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.notepkt.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    // implementation(libs.ktor.server.content.negotiation) // This is the alias for ktor-server-content-negotiation
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    // Logging
    implementation(libs.logback.classic)

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")

    // Database: HikariCP + PostgreSQL
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")

    // BCrypt password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Ktor Serialization & Kotlinx Serialization
    // Ktor's bridge for kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    // Ktor's ContentNegotiation plugin (you might already have this via an alias like libs.ktor.server.content.negotiation)
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    // The core Kotlinx Serialization JSON library
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Changed to 1.6.3, a common stable version. Verify your intended version. 1.7.3 is not a known release.


    // CORS support
    implementation("io.ktor:ktor-server-cors:2.3.12")
}
