// Tests serveur (spec/serveur.md § 10, D25) : lancent le PocketBase épinglé sur des bases vides
// avec les migrations et hooks du dépôt, puis jouent chaque route et chaque tentative de triche.
plugins {
    alias(libs.plugins.kotlin.jvm)
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

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

val dossierServeur = projectDir.parentFile

tasks.withType<Test>().configureEach {
    systemProperty("eruditus.serveur", dossierServeur.absolutePath)
    // Le résultat dépend du binaire, des migrations et des hooks : jamais « à jour », chaque
    // lancement rejoue tout.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
