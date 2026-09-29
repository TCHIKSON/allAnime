package com.allanime.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import com.allanime.tv.ui.theme.LocalGabarit

/**
 * Cadre commun aux ecrans principaux : la barre laterale, puis le contenu.
 *
 * La barre est posee **par-dessus** le contenu et non a cote : deployee, elle le
 * recouvre au lieu de le pousser. C'est ce qui permet a l'accueil de garder une
 * image plein cadre — la reflower a chaque deploiement donnerait une page qui
 * respire a chaque pression de touche.
 *
 * Le contenu recoit en parametre la marge a laisser a gauche pour ne pas passer sous
 * le rail replie. Chaque ecran en fait ce qu'il veut : l'accueil l'applique a ses
 * rangees mais pas a sa banniere, la recherche a tout son contenu.
 *
 * **La touche Retour fait deux choses, dans cet ordre** : elle ramene d'abord le
 * focus sur la barre, puis, si la barre l'a deja, elle remonte d'un cran
 * ([onRetourDepuisBarre]). C'est le comportement des interfaces TV grand public, et
 * il evite le piege habituel — quitter l'application d'une pression de trop.
 */
@Composable
fun CadrePrincipal(
    courante: DestinationPrincipale,
    onNaviguer: (DestinationPrincipale) -> Unit,
    onRetourDepuisBarre: () -> Unit,
    contenu: @Composable (margeDebut: Dp) -> Unit
) {
    val premierePorte = remember { FocusRequester() }
    val contenuPrincipal = remember { FocusRequester() }
    var barreFocalisee by remember { mutableStateOf(false) }

    var contenuFocalise by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // A l'arrivee sur un ecran, le focus doit entrer dans le **contenu**, pas
        // rester dans la barre : sinon celle-ci resterait deployee et masquerait
        // precisement ce que l'on vient d'ouvrir.
        //
        // Ce filet ne se declenche que si l'ecran n'a rien demande de lui-meme : la
        // recherche vise son clavier, l'accueil sa banniere, et leur choix doit
        // primer sur le repli generique applique ici.
        withFrameNanos { }
        withFrameNanos { }
        if (!contenuFocalise) runCatching { contenuPrincipal.requestFocus() }
    }

    BackHandler {
        if (barreFocalisee) {
            onRetourDepuisBarre()
        } else {
            // `runCatching` : la barre peut ne pas encore etre composee au tout
            // premier affichage, et une demande de focus sans cible leve.
            runCatching { premierePorte.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // `focusGroup` + `focusRequester` : demander le focus sur le groupe le donne
        // au premier element focalisable qu'il contient, quel que soit l'ecran.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { contenuFocalise = it.hasFocus }
                .focusGroup()
                .focusRequester(contenuPrincipal)
        ) {
            contenu(LocalGabarit.current.largeurRail)
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onFocusChanged { barreFocalisee = it.hasFocus }
                // Sortie explicite vers la droite.
                //
                // La recherche de focus de Compose est geometrique, et la barre est
                // posee **par-dessus** le contenu : leurs rectangles se chevauchent,
                // si bien qu'aucun candidat n'est franchement « a droite » d'une
                // entree du rail. La fleche droite ne menait donc nulle part, et il
                // fallait valider une destination pour quitter la barre. On designe
                // ici la cible au lieu de la laisser deduire.
                .focusProperties { right = contenuPrincipal }
        ) {
            BarreLaterale(
                courante = courante,
                // Le rail s'ouvre tant que le focus est quelque part dans la barre,
                // et se referme des qu'il la quitte pour le contenu.
                deployee = barreFocalisee,
                onNaviguer = onNaviguer,
                premierePorte = premierePorte
            )
        }
    }
}
