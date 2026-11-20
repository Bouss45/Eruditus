package com.eruditus.android.ui.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ressorts et durées de DESIGN.md § 6 ; tous coupés quand Android désactive les animations. */
class MouvementTest {

    private val actif = MouvementEruditus(animationsActives = true)
    private val coupe = MouvementEruditus(animationsActives = false)

    @Test
    fun `les ressorts ont l'amortissement et la raideur de DESIGN md`() {
        ressort("rebond", actif.rebond(), 0.45f, 600f)
        ressort("non", actif.non(), 0.25f, 1200f)
        ressort("arrivee", actif.arrivee(), 0.72f, 380f)
        assertEquals(390f, MouvementEruditus.NON_VITESSE_DP_PAR_S)
        assertEquals(1.03f, MouvementEruditus.REBOND_ECHELLE)
    }

    @Test
    fun `les durees, delais et courbes sont ceux de DESIGN md`() {
        duree("couleur", actif.couleur(), 120, 0, LinearEasing)
        duree("remplissage", actif.remplissage(), 280, 0, EmphasizedDecelerate)
        duree("trait", actif.trait(), 220, 0, CubicBezierEasing(0.2f, 0f, 0f, 1f))
        duree("trait après 140 ms", actif.trait(delai = 140), 220, 140, CubicBezierEasing(0.2f, 0f, 0f, 1f))
        duree("croix", actif.croix(), 160, 0, EmphasizedDecelerate)
        duree("revelation", actif.revelation(), 220, 320, FastOutSlowInEasing)
        duree("fondu de la bonne réponse au temps écoulé", actif.fonduBonneReponse(), 220, 0, FastOutSlowInEasing)
        duree("resultat", actif.resultat(), 200, 160, FastOutSlowInEasing)
        duree("sortie", actif.sortie(), 130, 0, EmphasizedAccelerate)
        duree("chrono", actif.chrono(), 300, 0, EmphasizedDecelerate)
        duree("ecran, entrée", actif.ecranEntree(), 200, 0, EmphasizedDecelerate)
        duree("ecran, sortie", actif.ecranSortie(), 100, 0, FastOutSlowInEasing)
        duree("boite", actif.boite(), 150, 0, FastOutSlowInEasing)
        duree("message", actif.message(), 200, 0, FastOutSlowInEasing)
        duree("élan du rebond", actif.elanRebond(), 80, 0, FastOutSlowInEasing)
        assertEquals(EmphasizedDecelerate, CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
        assertEquals(EmphasizedAccelerate, CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))
    }

    @Test
    fun `animations desactivees dans Android, chaque token devient snap`() {
        val specs: List<Pair<String, AnimationSpec<Float>>> = listOf(
            "rebond" to coupe.rebond(),
            "non" to coupe.non(),
            "arrivee" to coupe.arrivee(),
            "couleur" to coupe.couleur(),
            "remplissage" to coupe.remplissage(),
            "trait" to coupe.trait(delai = 440),
            "croix" to coupe.croix(),
            "revelation" to coupe.revelation(),
            "fondu" to coupe.fonduBonneReponse(),
            "resultat" to coupe.resultat(),
            "sortie" to coupe.sortie(),
            "chrono" to coupe.chrono(),
            "ecranEntree" to coupe.ecranEntree(),
            "ecranSortie" to coupe.ecranSortie(),
            "boite" to coupe.boite(),
            "message" to coupe.message(),
            "elanRebond" to coupe.elanRebond(),
        )
        for ((nom, spec) in specs) {
            assertTrue("$nom : snap attendu, ${spec::class.simpleName} trouvé", spec is SnapSpec)
            assertEquals("$nom : sans délai", 0, (spec as SnapSpec).delay)
        }
        assertTrue(coupe.indicateursTournent.not())
        assertTrue(actif.indicateursTournent)
    }

    private fun ressort(nom: String, spec: AnimationSpec<Float>, amortissement: Float, raideur: Float) {
        assertTrue("$nom : ressort attendu", spec is SpringSpec)
        spec as SpringSpec
        assertEquals("$nom : amortissement", amortissement, spec.dampingRatio)
        assertEquals("$nom : raideur", raideur, spec.stiffness)
    }

    private fun duree(nom: String, spec: AnimationSpec<Float>, ms: Int, delai: Int, courbe: Any) {
        assertTrue("$nom : tween attendu", spec is TweenSpec)
        spec as TweenSpec
        assertEquals("$nom : durée", ms, spec.durationMillis)
        assertEquals("$nom : délai", delai, spec.delay)
        assertEquals("$nom : courbe", courbe, spec.easing)
    }
}
