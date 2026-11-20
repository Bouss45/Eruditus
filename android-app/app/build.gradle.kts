import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signature release : fournie par la CI par des variables d'environnement (le keystore n'est jamais
// dans le dépôt). Sans ANDROID_KEYSTORE_PATH, l'APK release est produit non signé.
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")

// Numéro de version (D45) : -Pnumero=<n> → versionCode 1000 + n, versionName 1.0.<n>.
val numero = providers.gradleProperty("numero").map { it.trim().toInt() }.getOrElse(0)

// Adresse du serveur (D43, D24, D36) : variable ERUDITUS_URL (CI), sinon la ligne URL= de
// server/deploy.env (local, non versionné). Lue seulement quand BuildConfig est généré : les
// tests serveur n'en dépendent pas. Jamais affichée, même en cas d'erreur.
val fichierDeploiement = layout.projectDirectory.file("../../server/deploy.env")
val adresseServeur: Provider<String> = providers.environmentVariable("ERUDITUS_URL")
    .filter { it.isNotBlank() }
    .orElse(
        providers.fileContents(fichierDeploiement).asText.map { texte ->
            texte.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("URL=") }
                ?.removePrefix("URL=")
                ?.trim()
                ?.trim('"', '\'')
                .orEmpty()
        },
    )
    .orElse("")
    .map { brute ->
        val adresse = brute.trim().trimEnd('/')
        if (adresse.isEmpty()) {
            throw GradleException(
                "Adresse du serveur absente : définis ERUDITUS_URL ou la ligne URL= de " +
                    "server/deploy.env (SPEC D43).",
            )
        }
        if (!Regex("""https://[^\s"'\\]+""").matches(adresse)) {
            throw GradleException(
                "L'adresse du serveur doit commencer par https:// et ne contenir ni espace ni " +
                    "guillemet (SPEC D24, D43).",
            )
        }
        adresse
    }

android {
    namespace = "com.eruditus.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eruditus.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1000 + numero
        versionName = "1.0.$numero"
    }

    signingConfigs {
        if (releaseKeystorePath.isPresent) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Variante « Eruditus Preview » (D42) : s'installe à côté de l'app (identifiant
        // com.eruditus.android.preview, données séparées) et s'ouvre sur le catalogue du design
        // system. Construite et signée en local avec la clé de debug, jamais publiée.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
    }

    sourceSets {
        // Catalogue et aperçus du design system (D42) : builds debug et preview seulement, jamais
        // dans l'APK release (qui a son propre PremierEcran dans src/release/).
        for (buildType in listOf("debug", "preview")) {
            getByName(buildType).kotlin.directories.add("src/catalogue/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Adresse du serveur (D43).
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("fr")
    }
}

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "SERVEUR_URL",
            adresseServeur.map { BuildConfigField("String", "\"$it\"", "Adresse du serveur (D43)") },
        )
    }
}

// Les tests JVM comparent les tokens à DESIGN.md, à la racine du dépôt.
val racineDepot: String = rootDir.parentFile.absolutePath

tasks.withType<Test>().configureEach {
    systemProperty("eruditus.depot", racineDepot)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Annotations @Preview, utilisées seulement dans src/catalogue/ (retirées de la release par R8).
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
