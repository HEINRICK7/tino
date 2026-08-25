plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = "com.tino"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
