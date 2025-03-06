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
    // implementation(files("../libs/rla-0.5.1-uber.jar"))

    implementation(libs.guava)
    //implementation(libs.gson)
    //implementation(libs.jsr305)
    //implementation(libs.flogger)

    implementation(libs.jdom2)
    implementation(libs.slf4j)

    /*
    runtimeOnly(libs.slf4jJdk14)
    runtimeOnly(libs.floggerBackend)

    implementation(kotlin("stdlib-common", "1.6.20"))
    implementation(kotlin("stdlib", "1.6.20"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

    // A multiplatform Kotlin library for working with date and time.
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")

    // A multiplatform Kotlin library for working with protobuf.
    implementation("pro.streem.pbandk:pbandk-runtime:$pbandkVersion")

    // A multiplatform Kotlin library for Result monads
    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.15")

    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha12")

    testImplementation(libs.truth)
    testImplementation(libs.truthJava8Extension) */
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.eg.cli.RunShowSystem")
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}