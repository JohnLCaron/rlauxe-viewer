plugins {
    java
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":uibase"))
    implementation(files("/home/stormy/dev/github/rla/rlauxe/rla/build/libs/rla-0.5.1-uber.jar"))
    implementation(libs.slf4j)
    implementation(libs.logback.classic)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.rlauxe.viewer.ViewerMain")
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}