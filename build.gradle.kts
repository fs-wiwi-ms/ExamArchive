import gg.jte.ContentType

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.3.1"
    id("gg.jte.gradle") version "3.1.15"
}

group = "ms.wiwi.examarchive"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:7.0.0")
    implementation("io.javalin:javalin-rendering-jte:7.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("gg.jte:jte:3.1.15")
}

jte {
    sourceDirectory = file("src/main/jte").toPath()
    generate()
    contentType = ContentType.Html
}

application {
    mainClass.set("ms.wiwi.examarchive.ExamArchive")
}

tasks.compileJava {
    sourceCompatibility = JavaVersion.VERSION_25.toString()
    targetCompatibility = JavaVersion.VERSION_25.toString()
    options.encoding = "UTF-8"
}