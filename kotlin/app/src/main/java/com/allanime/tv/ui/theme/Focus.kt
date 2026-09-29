package com.allanime.tv.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Reperes communs a tous les etats de focus.
 *
 * Ils sont rassembles ici pour une raison simple : sur un televiseur, le focus est
 * le curseur. S'il ne se comporte pas exactement pareil d'un ecran a l'autre,
 * l'utilisateur perd le fil de l'endroit ou il se trouve. Une seule definition, donc,
 * et tous les composants s'y referent.
 */
object Focus {

    /**
     * Agrandissement d'une affiche survolee.
     *
     * Volontairement marque : c'est le retour visuel principal de la telecommande, et
     * un agrandissement timide se percoit mal a trois metres. Les rangees reservent
     * l'espace necessaire pour qu'une carte agrandie ne soit pas coupee par sa voisine.
     */
    const val ECHELLE_AFFICHE = 1.12f

    /** Agrandissement d'une carte large — vignette d'episode, carte de reprise. */
    const val ECHELLE_CARTE = 1.06f

    /** Agrandissement d'un element deja large : entree de liste, touche de clavier. */
    const val ECHELLE_DISCRETE = 1.04f

    /** Epaisseur de la bordure blanche qui entoure l'element survole. */
    val BORDURE = 3.dp

    /** Portee du halo bleute pose sous l'element survole. */
    val HALO = 20.dp

    /**
     * Duree des transitions de focus.
     *
     * Assez courte pour que la navigation reste immediate, assez longue pour que
     * l'oeil suive le deplacement au lieu de le subir.
     */
    const val DUREE_MS = 160
}

/**
 * Halo bleute pose sous un element survole.
 *
 * L'ombre coloree n'existe qu'a partir d'Android 9 ; en dessous, l'ombre reste noire
 * et l'element parait simplement souleve. Dans les deux cas la bordure blanche, elle,
 * est toujours la : le focus ne depend jamais du halo seul.
 */
@Composable
fun Modifier.haloFocus(actif: Boolean, forme: Shape): Modifier {
    val portee by animateDpAsState(
        targetValue = if (actif) Focus.HALO else 0.dp,
        animationSpec = tween(durationMillis = Focus.DUREE_MS, easing = FastOutSlowInEasing),
        label = "halo"
    )

    if (portee == 0.dp) return this

    return shadow(
        elevation = portee,
        shape = forme,
        clip = false,
        ambientColor = FocusGlow,
        spotColor = FocusGlow
    )
}
