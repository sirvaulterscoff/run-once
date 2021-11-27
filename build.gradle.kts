import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.32"
    id("com.github.ben-manes.versions") version "0.38.0"
}

allprojects {
    apply(plugin = "com.github.ben-manes.versions")

    group = "run-once"

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}