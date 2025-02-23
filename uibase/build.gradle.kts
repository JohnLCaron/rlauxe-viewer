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
    implementation(libs.autoValueAnnotations)
    implementation(libs.guava)
    implementation(libs.jdom2)
    implementation(libs.jgoodies)
    // implementation(libs.jsr305)
    implementation(libs.slf4j)

    annotationProcessor(libs.autoValue)

   //  testImplementation(project(":cdm-core"))

    testImplementation(libs.junit)
    // testImplementation(libs.mockitoCore)
    testImplementation(libs.truth)
}

tasks.test {
    // Tell java to use ucar.util.prefs.PreferencesExtFactory to generate preference objects
    // Important for ucar.util.prefs.TestJavaUtilPreferences
    systemProperties(
        Pair("java.util.prefs.PreferencesFactory", "ucar.util.prefs.PreferencesExtFactory")
    )
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "UI base library"
        )
    }
}