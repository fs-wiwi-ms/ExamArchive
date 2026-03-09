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
    //WEB
    implementation("io.javalin:javalin:7.0.0")
    implementation("io.javalin:javalin-rendering-jte:7.0.0")
    implementation("gg.jte:jte:3.1.15")
    //LOG
    implementation("org.slf4j:slf4j-simple:2.0.17")
    //DB
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("org.flywaydb:flyway-core:12.0.3")
    implementation("org.flywaydb:flyway-database-postgresql:12.0.3")
    //AUTH
    implementation("com.nimbusds:oauth2-oidc-sdk:11.33")
}

jte {
    sourceDirectory = file("src/main/jte").toPath()
    generate()
    contentType = ContentType.Html
}

application {
    mainClass.set("ms.wiwi.examarchive.ExamArchive")
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.compileJava {
    sourceCompatibility = JavaVersion.VERSION_25.toString()
    targetCompatibility = JavaVersion.VERSION_25.toString()
    options.encoding = "UTF-8"
}