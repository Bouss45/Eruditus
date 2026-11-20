package com.eruditus.android.ui.designsystem.components

import kotlin.math.ceil

/**
 * Règles du chrono de la partie (DESIGN.md § 6 et § 7, spec/ecrans.md § 9), calées sur `restantMs`
 * du serveur. Vérifiées par `ReglesChronoTest`.
 */
object ReglesChrono {
    /** Seuils annoncés par TalkBack, en secondes. */
    private val SEUILS = listOf(10, 5)

    /** Secondes affichées : arrondies au-dessus (20 au départ, 1 pendant la dernière, puis 0). */
    fun secondesAffichees(restantMs: Long): Int =
        if (restantMs <= 0) 0 else ceil(restantMs / 1000.0).toInt()

    /**
     * Largeur de la barre, entre 0 et 1. [paliers] (animations désactivées) : la barre avance d'une
     * seconde à la fois, comme les secondes affichées.
     */
    fun fraction(restantMs: Long, totalMs: Long, paliers: Boolean): Float {
        if (totalMs <= 0) return 0f
        val restant = if (paliers) secondesAffichees(restantMs) * 1000L else restantMs
        return (restant.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    /**
     * Seuil (10 ou 5 s) franchi entre [precedentMs] et [restantMs], à annoncer par TalkBack ; le
     * plus proche si les deux le sont d'un coup ; rien au premier affichage ni quand le temps remonte.
     */
    fun annonce(precedentMs: Long?, restantMs: Long): Int? {
        if (precedentMs == null) return null
        val avant = secondesAffichees(precedentMs)
        val apres = secondesAffichees(restantMs)
        return SEUILS.filter { avant > it && apres <= it }.minOrNull()
    }
}
