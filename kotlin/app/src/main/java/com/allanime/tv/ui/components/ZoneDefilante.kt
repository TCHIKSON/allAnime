package com.allanime.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.allanime.tv.ui.theme.FocusHalo
import com.allanime.tv.ui.theme.Focus
import kotlinx.coroutines.launch

/**
 * Bloc de texte long, parcourable a la telecommande.
 *
 * **Le probleme qu'il resout** : sur un televiseur, une page ne defile pas toute
 * seule — elle suit le focus. Un pave de texte, qui n'est ni un bouton ni une carte,
 * n'a rien de focalisable : il reste donc a l'ecran tel quel, et tout ce qui depasse
 * la hauteur disponible devient litteralement inatteignable.
 *
 * La zone se rend donc focalisable elle-meme et interprete les fleches haut et bas
 * comme un defilement. Elle ne retient la touche que **tant qu'il reste quelque chose
 * a lire** : arrivee en bas, elle laisse la fleche repartir vers l'element suivant,
 * ce qui evite le piege classique d'un bloc dont on ne peut plus sortir.
 *
 * Le contour blanc apparait au focus, comme pour n'importe quel element survole :
 * c'est ce qui indique que les fleches agissent ici et pas ailleurs.
 */
@Composable
fun ZoneDefilante(
    modifier: Modifier = Modifier,
    pas: Float = PAS_DEFILEMENT,
    contenu: @Composable ColumnScope.() -> Unit
) {
    val defilement = rememberScrollState()
    val portee = rememberCoroutineScope()
    var focalisee by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(12.dp)

    val contour by animateColorAsState(
        targetValue = if (focalisee) FocusHalo else Color.Transparent,
        animationSpec = tween(Focus.DUREE_MS),
        label = "contour-zone"
    )

    Column(
        modifier = modifier
            .clip(forme)
            .background(Color.Transparent)
            .border(width = 2.dp, color = contour, shape = forme)
            .onFocusChanged { focalisee = it.isFocused }
            .onPreviewKeyEvent { evenement ->
                if (evenement.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (evenement.key) {
                    Key.DirectionDown -> defiler(
                        possible = defilement.canScrollForward,
                        portee = portee,
                        defilement = defilement,
                        distance = pas
                    )

                    Key.DirectionUp -> defiler(
                        possible = defilement.canScrollBackward,
                        portee = portee,
                        defilement = defilement,
                        distance = -pas
                    )

                    else -> false
                }
            }
            .focusable()
            .verticalScroll(defilement)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        content = contenu
    )
}

/**
 * @return `true` si la touche a ete consommee, c'est-a-dire s'il restait quelque
 *   chose a faire defiler dans cette direction.
 */
private fun defiler(
    possible: Boolean,
    portee: kotlinx.coroutines.CoroutineScope,
    defilement: androidx.compose.foundation.ScrollState,
    distance: Float
): Boolean {
    if (!possible) return false
    portee.launch { defilement.animateScrollBy(distance) }
    return true
}

/**
 * Distance parcourue par pression, en pixels.
 *
 * Environ deux tiers d'ecran de television : assez pour avancer vite, pas assez pour
 * perdre la ligne qu'on etait en train de lire.
 */
private const val PAS_DEFILEMENT = 260f
