import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.example.CustomPlugin

apply<CustomPlugin>()

plugins {
    kotlin("jvm") version "1.9.0"
    id ("java-gradle-plugin")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}