plugins {
    kotlin("jvm")
}

version = "1.0"

repositories {
    mavenCentral()
}


dependencies {
    implementation(kotlin("stdlib"))
    api(project(":run-once-service"))
    api("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.3")
    api("com.fasterxml.jackson.core:jackson-databind:2.12.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.4.3")
}
