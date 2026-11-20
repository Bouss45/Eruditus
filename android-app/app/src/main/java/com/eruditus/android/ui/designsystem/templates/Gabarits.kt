package com.eruditus.android.ui.designsystem.templates

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.components.BarreHaut
import com.eruditus.android.ui.designsystem.components.ChargementEcran
import com.eruditus.android.ui.designsystem.components.EtatSysteme
import com.eruditus.android.ui.designsystem.components.IconeEtat
import com.eruditus.android.ui.designsystem.components.Marque
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.RangeeBoutons
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.components.Statistiques
import com.eruditus.android.ui.designsystem.theme.Eruditus

/*
 * Gabarits d'écran (DESIGN.md § 8). Un écran = un gabarit + des composants. À 200 %, seul le
 * contenu défile : la barre du haut et la zone du bas restent visibles.
 */

/** Marque du gabarit Information : à 150 du haut du contenu (planche Écrans · Accès). */
private val HAUT_MARQUE = 150.dp

/** Icône d'état du gabarit Information : à 96 (planche Écrans · Système). */
private val HAUT_ICONE = 96.dp

/**
 * Formulaire (Créer un compte, Se connecter, Mot de passe oublié) : `BarreHaut` retour ; [contenu] :
 * texte facultatif, `Champ`s, `ErreurFormulaire`, `Lien` ; espace ; [action] : le `BoutonPrincipal`.
 * Clavier ouvert : la zone du bas monte au-dessus, le contenu défile.
 */
@Composable
fun GabaritFormulaire(
    titre: String,
    onRetour: () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    message: Message? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Squelette(
        modifier = modifier,
        barre = { BarreHaut(titre, onRetour) },
        message = message,
        bas = { action() },
        contenu = contenu,
    )
}

/**
 * Information (Bienvenue, Code de récupération, Résultat, Pas de connexion, Mise à jour
 * nécessaire) : sans barre ; [marque] (sa phrase, à 150) ou [icone] (à 96) facultatifs ; [titre]
 * en `titre-1` ; [contenu] : texte, code, chiffres ; espace ; [secondaire] facultatif ;
 * [principal]. En haut à gauche, jamais centré : il se lit comme une page.
 */
@Composable
fun GabaritInformation(
    principal: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    message: Message? = null,
    marque: String? = null,
    icone: EtatSysteme? = null,
    titre: String? = null,
    secondaire: (@Composable () -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit = {},
) {
    Squelette(
        modifier = modifier,
        message = message,
        bas = {
            secondaire?.invoke()
            principal()
        },
    ) {
        when {
            marque != null -> Marque(marque, Modifier.padding(top = HAUT_MARQUE))
            icone != null -> IconeEtat(icone, Modifier.padding(top = HAUT_ICONE))
        }
        if (titre != null) {
            // Titre-1 à 24 du haut, ou à 24 sous l'icône (12 + 12).
            val haut = if (marque != null) 0.dp else if (icone != null) Eruditus.espacements.blocs else Eruditus.espacements.boite
            TitrePage(titre, Modifier.padding(top = haut))
        }
        contenu()
    }
}

/**
 * Accueil : [titre] (le pseudo, `titre-1`, à 24) ; [statistiques] (8) ; espace ; deux petits
 * `BoutonSecondaire` côte à côte ([petitsBoutons]) ; « Jouer » ([jouer], `BoutonPrincipal` grand).
 * Rien d'autre. [jouer] nul : pas encore de partie (SPEC D57). [chargement] : premier chargement,
 * `ChargementEcran` à la place du contenu.
 */
@Composable
fun GabaritAccueil(
    titre: String,
    statistiques: List<Statistique>,
    petitsBoutons: @Composable () -> Unit,
    jouer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    message: Message? = null,
    chargement: Boolean = false,
) {
    Squelette(
        modifier = modifier,
        message = message,
        bas = {
            RangeeBoutons(content = petitsBoutons)
            jouer?.invoke()
        },
    ) {
        if (chargement) {
            ChargementEcran(Modifier.weight(1f))
        } else {
            TitrePage(titre, Modifier.padding(top = Eruditus.espacements.boite))
            Statistiques(statistiques, Modifier.padding(top = Eruditus.espacements.icone))
        }
    }
}

/**
 * Liste (Classement, Compte) : `BarreHaut` retour ; [contenu] : des `ListeGroupee` ; [pied] en
 * `petit` centré, sous la liste ou sous la zone du bas ([bas] : actions du Compte). [chargement] :
 * `ChargementEcran` à la place du contenu ; [tirage] : tirer vers le bas recharge.
 */
@Composable
fun GabaritListe(
    titre: String,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    message: Message? = null,
    chargement: Boolean = false,
    tirage: Tirage? = null,
    pied: String? = null,
    bas: (@Composable ColumnScope.() -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Squelette(
        modifier = modifier,
        barre = { BarreHaut(titre, onRetour) },
        message = message,
        tirage = tirage.takeUnless { chargement },
        bas = bas?.let { actions ->
            {
                actions()
                pied?.let { Pied(it) }
            }
        },
    ) {
        if (chargement) {
            ChargementEcran(Modifier.weight(1f))
        } else {
            contenu()
            if (bas == null) pied?.let { Pied(it) }
        }
    }
}

/** Titre d'une page sans barre : `titre-1`, annoncé comme titre. */
@Composable
private fun TitrePage(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        modifier = modifier.semantics { heading() },
        style = Eruditus.typo.titre1,
        color = Eruditus.couleurs.texte,
    )
}

/** Pied d'une liste : `petit` centré, `texte-secondaire`, décalé de 4. */
@Composable
private fun Pied(texte: String) {
    Text(
        texte,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Eruditus.espacements.decalage),
        style = Eruditus.typo.petit,
        color = Eruditus.couleurs.texteSecondaire,
        textAlign = TextAlign.Center,
    )
}
