// Notes de version des Releases (spec/publication.md § 3, D49) : module JVM sans Android, repris
// de MuscleNote, lancé par release.yml (`:release-notes:run`) et testé avec les tests JVM de l'app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

application {
    mainClass = "com.eruditus.releasenotes.MainKt"
}

// Les tests comparent la liste des motifs à `paths` de .github/workflows/release.yml.
val racineDepot: String = rootDir.parentFile.absolutePath

tasks.withType<Test>().configureEach {
    systemProperty("eruditus.depot", racineDepot)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    testImplementation(libs.junit)
}
