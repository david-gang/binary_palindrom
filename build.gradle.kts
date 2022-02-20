plugins {
    id("org.jetbrains.kotlin.jvm").version("1.6.10")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java){
    kotlinOptions {
        jvmTarget = "17"
    }
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
            from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}