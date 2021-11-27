val springBootStarterR2dbcVersion: String by project
val reactorKotlinExtensionsVersion: String by project
val jacksonDatabindVersion: String by project
plugins {
    kotlin("jvm")
    id("io.kotlintest") version "1.1.1"
    id("jacoco")
}

group = "runonce"
version = "1.0"

repositories {
    mavenCentral()
}

repositories {
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}
jacoco {
    toolVersion = "0.8.7-SNAPSHOT"

}

tasks.test { useJUnitPlatform() }
tasks.build {
    finalizedBy (tasks.jacocoTestReport)
}
dependencies {
    implementation(kotlin("stdlib"))
    api(project(":run-once-service"))
    api("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootStarterR2dbcVersion")
    api("io.projectreactor.kotlin:reactor-kotlin-extensions:$reactorKotlinExtensionsVersion")
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")


    testImplementation("com.h2database:h2:1.4.197")
    testImplementation("io.r2dbc:r2dbc-h2:0.8.4.RELEASE")
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.4.5") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation( "org.jetbrains.kotlin:kotlin-test")
    testImplementation( "org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation( "io.kotlintest:kotlintest-extensions-spring:3.4.2")
    testImplementation( "io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation( "org.junit.jupiter:junit-jupiter-api:5.3.1")
    testImplementation ("org.junit.jupiter:junit-jupiter-engine:5.3.1")
    testImplementation("io.mockk:mockk:1.11.0")
    testImplementation("io.projectreactor:reactor-test:3.4.5")

}
