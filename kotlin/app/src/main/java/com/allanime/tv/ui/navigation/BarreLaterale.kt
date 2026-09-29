package com.allanime.tv.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.FocusHalo
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Les destinations que la barre laterale dessert.
 *
 * Cinq entrees, toutes adossees a des donnees que l'application possede reellement :
 * le catalogue distingue les longs metrages du reste ([FILMS] et [SERIES]), et le
 * site publie un planning hebdomadaire ([PLANNING]). Il n'y a volontairement ni
 * rubrique « Originals » — aucune source ne dit qui produit quoi — ni « Profils » :
 * l'application n'a pas de comptes, l'historique appartient au televiseur.
 */
enum class DestinationPrincipale(val libelle: Int) {
    RECHERCHE(R.string.nav_search),
    ACCUEIL(R.string.nav_home),
    SERIES(R.string.nav_series),
    FILMS(R.string.nav_films),
    PLANNING(R.string.nav_planning)
}

/**
 * Barre de navigation laterale, retractable.
 *
 * Au repos elle ne montre que ses icones ; des qu'une de ses entrees prend le focus,
 * elle s'elargit et affiche les libelles par-dessus le contenu, sans le deplacer.
 * C'est ce qui permet de garder une banniere plein cadre a l'accueil : la barre la
 * recouvre le temps qu'on la parcourt, puis lui rend la place.
 *
 * Deux etats se superposent et ne doivent pas etre confondus :
 *
 *  - **la destination courante**, marquee en corail avec son trait vertical : c'est
 *    l'ecran qu'on regarde ;
 *  - **le focus**, marque en blanc : c'est l'entree qu'on survole a la telecommande.
 *
 * @param courante destination affichee, ou `null` pour un ecran qui n'en est pas une.
 * @param deployee `true` quand la barre doit montrer ses libelles. L'etat est tenu
 *   par le cadre et non ici : lui seul sait si le focus est **quelque part** dans la
 *   barre, alors qu'une entree ne connait que son propre focus et ne saurait donc
 *   jamais quand refermer le rail.
 * @param premierePorte recoit le focus quand on demande la barre par la touche Retour.
 */
@Composable
fun BarreLaterale(
    courante: DestinationPrincipale?,
    deployee: Boolean,
    onNaviguer: (DestinationPrincipale) -> Unit,
    premierePorte: FocusRequester,
    modifier: Modifier = Modifier
) {
    val gabarit = LocalGabarit.current

    val largeur by animateDpAsState(
        targetValue = if (deployee) gabarit.largeurRailDeploye else gabarit.largeurRail,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "largeur-barre"
    )
    // Les libelles n'apparaissent qu'une fois la barre presque ouverte : les faire
    // grandir avec elle donnerait un texte comprime pendant toute l'animation.
    val opaciteLibelles by animateFloatAsState(
        targetValue = if (deployee) 1f else 0f,
        animationSpec = tween(durationMillis = 160, delayMillis = if (deployee) 80 else 0),
        label = "libelles-barre"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(largeur)
            .background(
                Brush.horizontalGradient(
                    if (deployee) {
                        listOf(InkBackground, InkBackground, InkBackground.copy(alpha = 0.92f))
                    } else {
                        listOf(InkBackground, InkBackground.copy(alpha = 0.55f), Color.Transparent)
                    }
                )
            )
            .padding(vertical = gabarit.margeDebut),
        verticalArrangement = Arrangement.spacedBy(gabarit.facteur.let { (6 * it).dp })
    ) {
        DestinationPrincipale.entries.forEachIndexed { rang, destination ->
            EntreeBarre(
                destination = destination,
                active = destination == courante,
                deployee = deployee,
                opaciteLibelle = opaciteLibelles,
                onClick = { onNaviguer(destination) },
                modifier = Modifier
                    .padding(horizontal = (12 * gabarit.facteur).dp)
                    .then(
                        if (rang == 0) Modifier.focusRequester(premierePorte) else Modifier
                    )
            )
        }
    }
}

/**
 * Une entree du rail : son icone, et son libelle quand la barre est ouverte.
 *
 * La pastille de fond ne s'allume qu'au focus. L'entree courante, elle, se signale
 * par son trait corail et par la couleur de son icone — visibles meme quand la barre
 * est repliee et que le libelle est absent.
 */
@Composable
private fun EntreeBarre(
    destination: DestinationPrincipale,
    active: Boolean,
    deployee: Boolean,
    opaciteLibelle: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gabarit = LocalGabarit.current
    var focalisee by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape((10 * gabarit.facteur).dp)

    val teinte by animateColorAsState(
        targetValue = when {
            focalisee -> PureWhite
            active -> Coral
            else -> MistGrey
        },
        animationSpec = tween(Focus.DUREE_MS),
        label = "teinte-entree"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(gabarit.hauteurEntreeRail)
            .onFocusChanged { focalisee = it.isFocused },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(forme),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MistGrey,
            focusedContainerColor = InkElevated,
            focusedContentColor = PureWhite
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(
            focusedScale = Focus.ECHELLE_DISCRETE
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, FocusHalo),
                shape = forme
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trait de l'ecran courant, colle au bord gauche de l'entree.
            Box(
                modifier = Modifier
                    .padding(start = (2 * gabarit.facteur).dp)
                    .width((3 * gabarit.facteur).dp)
                    .height((22 * gabarit.facteur).dp)
                    .clip(RoundedCornerShape((2 * gabarit.facteur).dp))
                    .background(if (active) Coral else Color.Transparent)
            )

            Box(
                modifier = Modifier.size(
                    width = gabarit.largeurRail - (30 * gabarit.facteur).dp,
                    height = gabarit.hauteurEntreeRail
                ),
                contentAlignment = Alignment.Center
            ) {
                IconeDestination(
                    destination = destination,
                    couleur = teinte,
                    taille = gabarit.tailleIconeRail
                )
            }

            if (deployee) {
                Text(
                    text = stringResource(destination.libelle),
                    style = MaterialTheme.typography.titleSmall,
                    color = teinte,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .alpha(opaciteLibelle)
                        .padding(end = (12 * gabarit.facteur).dp)
                )
            }
        }
    }
}

@Composable
private fun IconeDestination(
    destination: DestinationPrincipale,
    couleur: Color,
    taille: Dp
) {
    when (destination) {
        DestinationPrincipale.RECHERCHE -> IconeRecherche(couleur, taille = taille)
        DestinationPrincipale.ACCUEIL -> IconeAccueil(couleur, taille = taille)
        DestinationPrincipale.SERIES -> IconeSeries(couleur, taille = taille)
        DestinationPrincipale.FILMS -> IconeFilms(couleur, taille = taille)
        DestinationPrincipale.PLANNING -> IconePlanning(couleur, taille = taille)
    }
}
