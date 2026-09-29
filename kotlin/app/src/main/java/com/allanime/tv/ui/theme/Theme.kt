package com.allanime.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Charte graphique de l'application.
 *
 * Deux familles de couleurs, qui ne jouent pas le meme role :
 *
 *  - **le fond**, un bleu nuit tres sombre plutot qu'un gris neutre. Sur un
 *    televiseur, un fond legerement bleute fait ressortir les affiches — presque
 *    toutes chaudes et saturees — mieux qu'un gris, qui les eteint ;
 *  - **le corail du logo**, reserve a ce qui est *choisi* : onglet actif, bouton
 *    principal, reperes de section.
 *
 * Le **focus** de la telecommande, lui, n'est ni l'un ni l'autre : il est blanc.
 * C'est la seule couleur qui ressorte sur n'importe quelle affiche, et la distinguer
 * du corail evite la confusion permanente entre « ce que je survole » et « ce qui est
 * selectionne ».
 */

// --- Fond et surfaces -------------------------------------------------------

/** Fond general : bleu nuit presque noir. */
val InkBackground = Color(0xFF080B12)

/** Surface posee sur le fond : cartes, champs, panneaux. */
val InkSurface = Color(0xFF121826)

/** Surface elevee : element survole, bloc de metadonnees, touche de clavier. */
val InkElevated = Color(0xFF1C2435)

/** Surface la plus haute : menu ouvert, boite de dialogue. */
val InkOverlay = Color(0xFF232C40)

// --- Marque -----------------------------------------------------------------

val Coral = Color(0xFFF94C4C)
val CoralBright = Color(0xFFFF6B6B)
val CoralDeep = Color(0xFFC93B3B)

// --- Texte ------------------------------------------------------------------

val PureWhite = Color(0xFFFFFFFF)

/** Texte secondaire : sous-titres, libelles, valeurs de second plan. */
val MistGrey = Color(0xFFA8AFC0)

/** Texte tertiaire : mentions discretes qu'on ne lit que si on les cherche. */
val SlateGrey = Color(0xFF6C7488)

val Charcoal = Color(0xFF242730)

// --- Focus ------------------------------------------------------------------

/** Bordure de l'element survole a la telecommande. */
val FocusHalo = PureWhite

/**
 * Halo bleute pose sous l'element survole.
 *
 * Il ne remplace pas la bordure : il l'accompagne, pour que l'element paraisse
 * eclaire plutot que simplement entoure.
 */
val FocusGlow = Color(0xFF7FB2FF)

private val AllAnimeColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = PureWhite,
    primaryContainer = CoralDeep,
    onPrimaryContainer = PureWhite,
    secondary = MistGrey,
    onSecondary = Charcoal,
    secondaryContainer = InkElevated,
    onSecondaryContainer = PureWhite,
    background = InkBackground,
    onBackground = PureWhite,
    surface = InkSurface,
    onSurface = PureWhite,
    surfaceVariant = InkElevated,
    onSurfaceVariant = MistGrey,
    error = CoralBright,
    onError = PureWhite
)

/**
 * Theme de l'application.
 *
 * Il calcule le [Gabarit] une fois pour l'ecran courant et le diffuse a tout l'arbre,
 * couleurs et typographie comprises. Aucun ecran n'a donc a savoir sur quel
 * televiseur il tourne : il lit `LocalGabarit.current` et en tire ses dimensions.
 */
@Composable
fun AllAnimeTheme(content: @Composable () -> Unit) {
    val gabarit = gabaritCourant()

    CompositionLocalProvider(LocalGabarit provides gabarit) {
        MaterialTheme(
            colorScheme = AllAnimeColorScheme,
            // La typographie suit le meme facteur que la mise en page : les laisser
            // diverger donnerait un texte qui retrecit a mesure que l'ecran grandit.
            typography = typographiePour(gabarit.facteur),
            content = content
        )
    }
}
