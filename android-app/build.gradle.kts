plugins {
    // Module JVM des tests serveur (server/tests), sans Android.
    alias(libs.plugins.kotlin.jvm) apply false
}
