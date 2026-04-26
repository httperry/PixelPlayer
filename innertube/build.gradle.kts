plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-client-encoding:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.brotli:dec:0.1.2")
    testImplementation("junit:junit:4.13.2")
}