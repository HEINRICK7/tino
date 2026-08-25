plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

dependencies {
    implementation("ai.koog:koog-agents:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(kotlin("test"))
}

sourceSets {
    main {
        kotlin.srcDir("../tino-agent-contracts/src/main/kotlin")
    }
}

kotlin {
    // The environment provides JDK 21; Koog requires Java 17+.
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
