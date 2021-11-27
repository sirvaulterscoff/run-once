plugins {
    kotlin("jvm")
}

group = "runonce"
version = "1.0"

repositories {
    mavenCentral()
}


dependencies {
    implementation(kotlin("stdlib"))
    api("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.3")
    api("com.fasterxml.jackson.core:jackson-databind:2.12.3")
}
