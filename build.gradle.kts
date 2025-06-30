val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
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
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-apache")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    
    // Environment variable loading from .env
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // DateTime handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    
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
