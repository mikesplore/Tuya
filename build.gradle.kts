val kotlin_version: String by project
val logback_version: String by project
val koin_version = "3.5.3"

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.2.0"
}

group = "com.mike"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-gson:3.2.0")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-apache")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    
    // Koin for dependency injection
    implementation("io.insert-koin:koin-core:$koin_version")
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // Environment variable loading from .env
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // DateTime handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    
    // Database dependencies
    implementation("org.jetbrains.exposed:exposed-core:0.47.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.47.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.47.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.47.0")
    implementation("org.postgresql:postgresql:42.7.0")
    
    // JWT Authentication
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("at.favre.lib:bcrypt:0.10.2")
    
    // XML Binding API (required for Java 9+)
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

// CLI task
tasks.register<JavaExec>("cli") {
    group = "application"
    description = "Run the Tuya CLI application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.mike.cli.TuyaCLIKt")
    
    // Pass command line arguments
    args = if (project.hasProperty("args")) {
        project.property("args").toString().split(" ")
    } else {
        emptyList()
    }
}

// Simple environment test task
tasks.register<JavaExec>("testEnvDirect") {
    group = "application"
    description = "Test .env file loading directly without Ktor"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.mike.test.EnvTest")
}

// Environment test task
tasks.register<JavaExec>("testEnv") {
    group = "application"
    description = "Test loading of environment variables"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.mike.tuya.config.EnvTestKt")
}

// Database initialization task with sample data
tasks.register<JavaExec>("initDatabase") {
    group = "database"
    description = "Initialize the database with sample data"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.mike.database.DatabaseInitKt")
}
