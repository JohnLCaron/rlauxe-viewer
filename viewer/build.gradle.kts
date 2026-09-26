plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val subprojectName = name
val subprojectVersion = version

dependencies {
    api(project(":rlauxe-uibase"))
    implementation(files("/home/stormy/dev/github/rla/rlauxe/cases/build/libs/rlauxe-cases-0.10.4.3-uber.jar"))
    // implementation(files("../libs/rlauxe-cases-0.10.4.3-uber.jar"))
    implementation(libs.flatlaf)
    implementation(libs.slf4j)
    implementation(libs.logback.classic)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(kotlin("test"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}


tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.rlauxe.viewer.ViewerMain")
        attributes("Implementation-Title" to subprojectName)
        attributes("Implementation-Version" to subprojectVersion)
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

