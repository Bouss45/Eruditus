package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Espace au-dessus d'un champ : 8 (DESIGN.md § 5, « haut d'un champ »), 0 dans une `Boite`. */
internal val LocalEspaceHautChamp = staticCompositionLocalOf<Dp?> { null }

/** Source toujours « en saisie » : montre l'état Saisie dans les aperçus et le catalogue. */
object SaisieForcee : MutableInteractionSource {
    override val interactions: Flow<Interaction> = flowOf(FocusInteraction.Focus())
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction): Boolean = true
}

/**
 * `Champ` (DESIGN.md § 7) : libellé `libelle` au-dessus, jamais dedans, écart 6 ; champ ≥ 56,
 * rayon 12, contour `contour-champ`, `surface`, saisie `choix` 400, marge 16 (56 avec l'œil) ;
 * œil : bouton de 48, icône 22 ; aide `petit` `texte-secondaire`, écart 6.
 *
 * États : vide, saisie (contour `accent` 2), erreur (contour `rouge` 2, icône 16 + `petit` 500
 * `rouge` à la place de l'aide), mot de passe masqué ou affiché, désactivé (pendant un envoi).
 * [typeContenu] marque le champ pour le remplissage automatique.
 *
 * [plusieursLignes] (D94) : haut de 5 lignes, grandit jusqu'à 10 puis défile en lui-même, texte en
 * haut à gauche, marge 16 tout autour ; Entrée passe à la ligne, majuscule en début de phrase.
 * [compteur] : « n / max » à droite de l'aide ou de l'erreur, `rouge` au-delà du maximum.
 */
@Composable
fun Champ(
    valeur: String,
    onValeurChange: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier,
    aide: String? = null,
    erreur: String? = null,
    motDePasse: Boolean = false,
    motDePasseAfficheAuDepart: Boolean = false,
    actif: Boolean = true,
    typeContenu: ContentType? = null,
    plusieursLignes: Boolean = false,
    compteur: Compteur? = null,
    optionsClavier: KeyboardOptions = when {
        motDePasse -> KeyboardOptions(keyboardType = KeyboardType.Password)
        plusieursLignes -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        else -> KeyboardOptions.Default
    },
    actionsClavier: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = Eruditus.couleurs
    val typo = Eruditus.typo
    val forme = Eruditus.rayons.champ
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focus by source.collectIsFocusedAsState()
    val enSaisie = actif && (focus || source === SaisieForcee)
    var affiche by rememberSaveable { mutableStateOf(motDePasseAfficheAuDepart) }

    val cibleContour = when {
        !actif -> c.separateur
        erreur != null -> c.rouge
        enSaisie -> c.accent
        else -> c.contourChamp
    }
    val contour by animateColorAsState(cibleContour, Eruditus.mouvement.couleur(), label = "contour")
    val epaisseur = if (actif && (erreur != null || enSaisie)) Eruditus.tailles.contourFort else Eruditus.tailles.contour
    val couleurTexte = if (actif) c.texte else c.desactiveTexte

    Column(
        modifier = modifier.padding(top = LocalEspaceHautChamp.current ?: Eruditus.espacements.icone),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            libelle,
            modifier = Modifier.clearAndSetSemantics { },
            style = typo.libelle,
            color = if (actif) c.texte else c.texteSecondaire,
        )
        BasicTextField(
            value = valeur,
            onValueChange = onValeurChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = libelle
                    typeContenu?.let { contentType = it }
                    erreur?.let { error(it) }
                },
            enabled = actif,
            singleLine = !plusieursLignes,
            minLines = if (plusieursLignes) 5 else 1,
            maxLines = if (plusieursLignes) 10 else 1,
            textStyle = typo.choix.copy(fontWeight = FontWeight.Normal, color = couleurTexte),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (motDePasse && !affiche) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = optionsClavier,
            keyboardActions = actionsClavier,
            interactionSource = source,
            decorationBox = { saisie ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Eruditus.tailles.bouton)
                        .background(if (actif) c.surface else c.desactiveFond, forme)
                        .border(epaisseur, contour, forme),
                    contentAlignment = if (plusieursLignes) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    val vertical = if (plusieursLignes) 16.dp else 8.dp
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = if (motDePasse) 56.dp else 16.dp, top = vertical, bottom = vertical),
                    ) { saisie() }
                    if (motDePasse) {
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = Eruditus.espacements.decalage)) {
                            BoutonIcone(
                                image = if (affiche) Icones.OeilBarre else Icones.Oeil,
                                tailleIcone = 22.dp,
                                couleur = if (actif) c.texteSecondaire else c.desactiveTexte,
                                description = stringResource(
                                    if (affiche) R.string.ds_masquer_mot_de_passe else R.string.ds_afficher_mot_de_passe,
                                ),
                                onClick = { affiche = !affiche },
                                actif = actif,
                            )
                        }
                    }
                }
            },
        )
        if (compteur == null) {
            AideOuErreur(aide, erreur)
        } else {
            // L'aide ou l'erreur passe à la ligne avant le compteur, qui reste à droite.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { AideOuErreur(aide, erreur) }
                CompteurChamp(compteur)
            }
        }
    }
}

/** Le compteur d'un `Champ` : [longueur] sur [maximum] caractères. */
data class Compteur(val longueur: Int, val maximum: Int)

/** Sous le champ : l'erreur (icône 16 + `petit` 500 `rouge`, annoncée), sinon l'aide. */
@Composable
private fun AideOuErreur(aide: String?, erreur: String?) {
    val c = Eruditus.couleurs
    val typo = Eruditus.typo
    when {
        erreur != null -> {
            val hauteurLigne = with(LocalDensity.current) { typo.petit.lineHeight.toDp() }
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.height(hauteurLigne), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.ErreurChamp, 16.dp, c.rouge)
                }
                Text(erreur, Modifier.weight(1f), style = typo.petit.copy(fontWeight = FontWeight.Medium), color = c.rouge)
            }
        }
        aide != null -> Text(aide, style = typo.petit, color = c.texteSecondaire)
    }
}

/**
 * « 12 / 1000 » en `petit` `texte-secondaire` tabulaire, `rouge` au-delà du maximum. TalkBack le
 * lit « 12 sur 1000 caractères » (« …, trop long » au-delà) quand on y arrive, jamais à chaque
 * frappe : pas de zone annoncée (D94).
 */
@Composable
private fun CompteurChamp(compteur: Compteur) {
    val (longueur, maximum) = compteur
    val trop = longueur > maximum
    val description = stringResource(if (trop) R.string.ds_compteur_trop_long else R.string.ds_compteur_description, longueur, maximum)
    Text(
        stringResource(R.string.ds_compteur, longueur, maximum),
        Modifier.clearAndSetSemantics { contentDescription = description },
        style = Eruditus.typo.petit.copy(fontFeatureSettings = "'tnum'"),
        color = if (trop) Eruditus.couleurs.rouge else Eruditus.couleurs.texteSecondaire,
    )
}
