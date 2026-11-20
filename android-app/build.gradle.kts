plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compile le Kotlin lui-même (« built-in Kotlin ») : déclarer le plugin ici, sans
    // l'appliquer, fixe la version de Kotlin utilisée.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Module JVM des tests serveur (server/tests), sans Android.
    alias(libs.plugins.kotlin.jvm) apply false
}
