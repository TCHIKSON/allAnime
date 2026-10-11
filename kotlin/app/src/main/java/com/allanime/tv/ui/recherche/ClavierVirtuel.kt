package com.allanime.tv.ui.recherche

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.ui.navigation.IconeEffacer
import com.allanime.tv.ui.navigation.IconeMicro
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.FocusGlow
import com.allanime.tv.ui.theme.Gabarit
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.InkSurface
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite
import com.allanime.tv.ui.theme.haloFocus

/**
 * Clavier virtuel pilote a la telecommande.
 *
 * Il remplace le clavier du systeme, qui sur un televiseur recouvre la moitie basse
 * de l'ecran — c'est-a-dire precisement les resultats qu'on cherche a filtrer. Ici la
 * grille est ancree a gauche, a cote d'eux : la liste se resserre a chaque touche,
 * sous les yeux de l'utilisateur.
 */

/**
 * Six colonnes : trente-six touches en six pressions au plus, dans les deux sens.
 *
 * Le nombre de colonnes ne bouge pas avec la taille de l'ecran — c'est la taille des
 * touches qui suit le gabarit. Un televiseur plus grand ne se regarde pas de plus
 * pres : y ajouter des colonnes rendrait juste chaque tuile plus petite a l'oeil.
 */
private const val COLONNES = Gabarit.COLONNES_CLAVIER

/**
 * Disposition : une grille rectangulaire, en **ordre alphabetique**.
 *
 * Deux choix, pour deux raisons differentes :
 *
 *  - **la grille plutot que le ruban** : un alphabet etale sur une seule ligne
 *    oblige a traverser vingt-cinq touches pour atteindre la derniere ; en grille,
 *    quatre pressions suffisent a rejoindre n'importe quel caractere ;
 *  - **l'ordre alphabetique plutot qu'azerty** : on ne tape pas a dix doigts avec une
 *    telecommande, on **cherche** une lettre du regard. Dans cette situation, l'ordre
 *    qu'on connait par coeur depuis l'enfance bat n'importe quelle disposition de
 *    machine a ecrire.
 */
private const val LETTRES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val CHIFFRES = "0123456789"

/**
 * Symboles proposes par la seconde page.
 *
 * Les plus utiles d'abord — tiret, apostrophe, deux-points, point d'exclamation :
 * c'est ce qu'on rencontre reellement dans un titre d'anime.
 *
 * Ils sont **exactement trente-six**, comme les lettres et les chiffres reunis, et ce
 * n'est pas un hasard : les deux pages occupent ainsi le meme nombre de rangees. La
 * touche de bascule garde donc sa place a l'ecran d'une page a l'autre, et le focus
 * reste dessus. Avec des pages de hauteurs differentes, elle se deplacait, Compose
 * n'y reconnaissait plus le meme element et le focus retombait hors du clavier.
 */
private const val SYMBOLES =
    "-_\'\".,:;!?()[]{}/" +
        "\\" +
        "&@#%+=*~\u00b0^<>|" +
        "\u0024\u20ac\u00a3\u00a7\u00a5"

/**
 * Une touche.
 *
 * Les formes sont distinguees parce qu'elles ne se dessinent pas pareil : un
 * caractere tient dans un carre, les trois actions occupent deux colonnes.
 */
private sealed interface Touche {
    data class Caractere(val valeur: String) : Touche
    data object Bascule : Touche
    data object Espace : Touche
    data object Effacer : Touche
}

/** Rangee d'actions, commune aux deux pages. */
private val ACTIONS = listOf(Touche.Bascule, Touche.Espace, Touche.Effacer)

/**
 * Les rangees de chaque page, calculees une fois.
 *
 * Declarees **apres** [COLONNES] : les proprietes de haut niveau sont initialisees
 * dans l'ordre du fichier, et lire la constante avant sa declaration donnerait zero.
 */
private val PAGE_LETTRES: List<List<Touche>> = buildList {
    (LETTRES + CHIFFRES)
        .map { Touche.Caractere(it.toString()) }
        .chunked(COLONNES)
        .forEach { add(it) }
    add(ACTIONS)
}

private val PAGE_SYMBOLES: List<List<Touche>> = buildList {
    SYMBOLES
        .map { Touche.Caractere(it.toString()) }
        .chunked(COLONNES)
        .forEach { add(it) }
    add(ACTIONS)
}

/**
 * @param onVocal lance la recherche vocale. La touche occupe une rangee entiere sous
 *   les actions, commune aux deux pages : elle ne change donc pas de place quand on
 *   bascule de l'une a l'autre.
 * @param ecouteVocale `true` pendant que le micro ecoute : la touche le dit, et un
 *   second appui arrete l'ecoute.
 * @param premiereTouche recoit le focus a l'ouverture de l'ecran.
 */
@Composable
fun ClavierVirtuel(
    onCaractere: (String) -> Unit,
    onEspace: () -> Unit,
    onEffacer: () -> Unit,
    onVocal: () -> Unit,
    ecouteVocale: Boolean,
    premiereTouche: FocusRequester,
    modifier: Modifier = Modifier
) {
    val gabarit = LocalGabarit.current
    var symboles by remember { mutableStateOf(false) }
    val rangees = if (symboles) PAGE_SYMBOLES else PAGE_LETTRES

    Column(
        modifier = modifier.width(gabarit.largeurClavier),
        verticalArrangement = Arrangement.spacedBy(gabarit.espacementTouche)
    ) {
        rangees.forEachIndexed { rangDeLigne, rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(gabarit.espacementTouche)) {
                rangee.forEachIndexed { rangDeTouche, touche ->
                    val ancrage = if (rangDeLigne == 0 && rangDeTouche == 0) {
                        Modifier.focusRequester(premiereTouche)
                    } else {
                        Modifier
                    }

                    when (touche) {
                        is Touche.Caractere -> SurfaceTouche(
                            onClick = { onCaractere(touche.valeur.lowercase()) },
                            modifier = ancrage.size(gabarit.coteTouche)
                        ) { focalisee ->
                            Text(
                                text = touche.valeur,
                                style = MaterialTheme.typography.titleMedium,
                                color = encreTouche(focalisee)
                            )
                        }

                        Touche.Bascule -> SurfaceTouche(
                            onClick = { symboles = !symboles },
                            modifier = ancrage
                                .width(gabarit.largeurToucheAction)
                                .height(gabarit.coteTouche)
                        ) { focalisee ->
                            Text(
                                text = stringResource(
                                    if (symboles) {
                                        R.string.search_keyboard_letters
                                    } else {
                                        R.string.search_keyboard_symbols
                                    }
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                // La seule touche qui change la page, donc la seule
                                // qui merite d'etre reperable sans etre lue.
                                color = if (focalisee) InkBackground else Coral
                            )
                        }

                        Touche.Espace -> SurfaceTouche(
                            onClick = onEspace,
                            modifier = ancrage
                                .width(gabarit.largeurToucheAction)
                                .height(gabarit.coteTouche)
                        ) { focalisee ->
                            Text(
                                text = stringResource(R.string.search_keyboard_space),
                                style = MaterialTheme.typography.labelMedium,
                                color = encreTouche(focalisee, discrete = true)
                            )
                        }

                        Touche.Effacer -> SurfaceTouche(
                            onClick = onEffacer,
                            modifier = ancrage
                                .width(gabarit.largeurToucheAction)
                                .height(gabarit.coteTouche)
                        ) { focalisee ->
                            IconeEffacer(
                                couleur = encreTouche(focalisee, discrete = true),
                                taille = gabarit.coteTouche * 0.5f
                            )
                        }
                    }
                }
            }
        }

        SurfaceTouche(
            onClick = onVocal,
            modifier = Modifier
                .width(gabarit.largeurClavier)
                .height(gabarit.coteTouche)
        ) { focalisee ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gabarit.espacementTouche),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconeMicro(
                    // Corail pendant l'ecoute, meme sous le focus : c'est le
                    // temoin « micro ouvert ».
                    couleur = if (focalisee && !ecouteVocale) InkBackground else Coral,
                    taille = gabarit.coteTouche * 0.5f
                )
                Text(
                    text = stringResource(
                        if (ecouteVocale) {
                            R.string.search_voice_listening
                        } else {
                            R.string.search_voice
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = encreTouche(focalisee)
                )
            }
        }
    }
}

/**
 * Socle commun des touches.
 *
 * **Le focus s'y marque par un remplissage, pas par une bordure.** C'est la seule
 * entorse a la regle de l'application, et elle est deliberee : sur une grille de
 * quarante tuiles serrees, un trait de deux points se repere mal, alors qu'une touche
 * qui s'allume en blanc saute aux yeux a trois metres. Le texte passe donc en sombre
 * pour rester lisible, et le halo bleute borde le tout.
 *
 * L'agrandissement, lui, reste discret : une touche survolee qui grossit trop mord
 * sur ses voisines et brouille la grille.
 *
 * @param contenu recoit l'etat de focus, dont il a besoin pour choisir sa couleur —
 *   ecrire du blanc sur une touche devenue blanche la rendrait invisible.
 */
@Composable
private fun SurfaceTouche(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable (focalisee: Boolean) -> Unit
) {
    val gabarit = LocalGabarit.current
    var focalisee by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape((8 * gabarit.facteur).dp)

    Surface(
        onClick = onClick,
        modifier = modifier
            .haloFocus(focalisee, forme)
            .onFocusChanged { focalisee = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = InkSurface,
            contentColor = MistGrey,
            focusedContainerColor = PureWhite,
            focusedContentColor = InkBackground
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Focus.ECHELLE_DISCRETE),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke((2 * gabarit.facteur).dp, FocusGlow),
                shape = forme
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            contenu(focalisee)
        }
    }
}

/** Encre d'une touche : sombre quand elle est allumee, claire au repos. */
@Composable
private fun encreTouche(focalisee: Boolean, discrete: Boolean = false): Color = when {
    focalisee -> InkBackground
    discrete -> MistGrey
    else -> PureWhite
}
