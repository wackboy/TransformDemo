plugins {
    id("java")
    id("groovy")
    id("java-gradle-plugin")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:3.5.3")
    implementation("com.android.tools.build:gradle-api:3.5.3")
    implementation("org.ow2.asm:asm:7.1")
    implementation("org.ow2.asm:asm-util:7.1")
    implementation("org.ow2.asm:asm-commons:7.1")
    implementation("commons-io:commons-io:2.6")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

