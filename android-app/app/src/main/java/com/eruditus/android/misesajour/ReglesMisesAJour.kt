package com.eruditus.android.misesajour

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Une Release publiée : version (`1.0.13`, le tag sans son `v`), page, et APK s'il y en a un. */
data class Release(val version: String, val page: String, val apk: String?)

/**
 * Ce que l'app garde des nouvelles versions (`mises-a-jour.json`, D64) : dates de la dernière
 * vérification réussie et du dernier échec (ms, 0 = jamais), dernière Release vue, version ignorée
 * par « Plus tard ».
 */
data class EtatMisesAJour(
    val reussieLe: Long = 0,
    val echecLe: Long = 0,
    val derniereVersion: String? = null,
    val apk: String? = null,
    val page: String? = null,
    val versionIgnoree: String? = null,
) {
    /** La dernière Release vue, si elle est complète. */
    val derniere: Release?
        get() {
            val version = derniereVersion ?: return null
            return Release(version, page ?: return null, apk)
        }
}

/**
 * Nouvelles versions (spec/regles.md § 6, D61 – D63), en fonctions pures testées sur la JVM,
 * reprises de MuscleNote (`UpdateRules`) sans la notification : versions et versionCode, réponse
 * de l'API GitHub, délais, annonce, Obtainium ou navigateur.
 */
object ReglesMisesAJour {
    const val DEPOT = "Bouss45/Eruditus"

    /** Dernière Release, ni brouillon ni préversion. */
    const val API = "https://api.github.com/repos/$DEPOT/releases/latest"

    /** Page de la dernière Release, quand aucune n'est connue (« Mettre à jour » en debug, par exemple). */
    const val PAGE_DERNIERE = "https://github.com/$DEPOT/releases/latest"

    /** 6 h après une vérification réussie, 1 h après un échec (§ 6.1). */
    const val DELAI_REUSSITE_MS = 6 * 3_600_000L
    const val DELAI_ECHEC_MS = 3_600_000L

    /** Obtainium, versions GitHub et F-Droid. */
    val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    /** Nom du Play Store, qu'Obtainium emprunte quand il installe par Shizuku (Eruditus n'y est pas). */
    const val PLAY_STORE = "com.android.vending"

    /** Où « Télécharger » et « Mettre à jour » envoient, dans l'ordre d'essai (D63). */
    sealed interface Cible {
        data class Obtainium(val paquet: String) : Cible
        data class Navigateur(val adresse: String) : Cible
    }

    /** Version d'une Release de `release.yml` : `1.0.<n>`. */
    private val versionDeRelease = Regex("""1\.0\.(\d+)""")

    /** Nombres d'une version : `v1.0.12` → [1, 0, 12] ; `1.0.0-preview` → [1, 0, 0]. */
    fun nombres(version: String): List<Int> =
        Regex("""\d+""").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()

    /**
     * [candidate] est-elle plus récente que [actuelle] ? Nombre par nombre (`1.0.10` > `1.0.9`), un
     * nombre manquant valant 0. Une version sans nombre ne l'est jamais.
     */
    fun plusRecente(candidate: String, actuelle: String): Boolean {
        val a = nombres(candidate)
        if (a.isEmpty()) return false
        val b = nombres(actuelle)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** versionCode d'une Release de `release.yml` : `1.0.n` → 1000 + n ; null pour une autre forme. */
    fun versionCodeDeRelease(version: String): Int? =
        versionDeRelease.matchEntire(version)?.groupValues?.get(1)?.toIntOrNull()?.let { 1000 + it }

    /** Par le versionCode pour une version `1.0.n`, sinon nombre par nombre contre [nomInstalle]. */
    fun plusRecenteQueInstallee(version: String, nomInstalle: String, codeInstalle: Int): Boolean {
        val code = versionCodeDeRelease(version) ?: return plusRecente(version, nomInstalle)
        return code > codeInstalle
    }

    /**
     * Réponse de `releases/latest` → [Release] ; null si elle est illisible, sans tag utilisable ou
     * sans page, ou s'il s'agit d'un brouillon ou d'une préversion. APK : le premier `.apk`.
     */
    fun lireRelease(corps: String): Release? {
        val objet = try {
            Json.parseToJsonElement(corps) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        fun texte(o: JsonObject, nom: String) = (o[nom] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun booleen(nom: String) = (objet[nom] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: false
        if (booleen("draft") || booleen("prerelease")) return null
        val version = texte(objet, "tag_name")?.removePrefix("v")?.takeIf { nombres(it).isNotEmpty() } ?: return null
        val page = texte(objet, "html_url")?.takeIf { it.isNotEmpty() } ?: return null
        val apk = (objet["assets"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .firstOrNull { texte(it, "name")?.endsWith(".apk", ignoreCase = true) == true }
            ?.let { texte(it, "browser_download_url") }
        return Release(version, page, apk)
    }

    /**
     * Vérification due (§ 6.1) : 6 h après la dernière réussite et 1 h après le dernier échec ;
     * jamais, ou une date dans le futur (horloge reculée), comptent comme dus.
     */
    fun due(etat: EtatMisesAJour, maintenant: Long): Boolean {
        fun ecoule(depuis: Long, delai: Long) = depuis == 0L || depuis > maintenant || maintenant - depuis >= delai
        return ecoule(etat.reussieLe, DELAI_REUSSITE_MS) && ecoule(etat.echecLe, DELAI_ECHEC_MS)
    }

    /** Release à annoncer (§ 6.4) : plus récente que l'app installée et pas ignorée. */
    fun annonce(etat: EtatMisesAJour, nomInstalle: String, codeInstalle: Int): Release? =
        pourMettreAJour(etat, nomInstalle, codeInstalle)?.takeIf { it.version != etat.versionIgnoree }

    /** Release vers laquelle mettre à jour, même ignorée (« Mise à jour nécessaire ») ; null si aucune n'est plus récente. */
    fun pourMettreAJour(etat: EtatMisesAJour, nomInstalle: String, codeInstalle: Int): Release? =
        etat.derniere?.takeIf { plusRecenteQueInstallee(it.version, nomInstalle, codeInstalle) }

    /** Installée par Obtainium (§ 6.5) : installateur ou initiateur Obtainium, ou le Play Store qu'il emprunte. */
    fun installeeParObtainium(installateurs: List<String>): Boolean =
        installateurs.any { it in OBTAINIUM || it == PLAY_STORE }

    /**
     * « Télécharger » et « Mettre à jour » (D63) : Obtainium s'il a installé l'app (sa variante
     * d'abord), puis le navigateur sur l'APK de [release], sa page, ou la dernière Release du dépôt.
     */
    fun cibles(installateurs: List<String>, release: Release?): List<Cible> {
        val obtainium = if (installeeParObtainium(installateurs)) {
            (installateurs.filter { it in OBTAINIUM } + OBTAINIUM).distinct().map { Cible.Obtainium(it) }
        } else {
            emptyList()
        }
        return obtainium + Cible.Navigateur(release?.apk ?: release?.page ?: PAGE_DERNIERE)
    }
}
