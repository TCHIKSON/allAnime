package com.allanime.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min

/**
 * Mise a l'echelle de l'interface selon le televiseur.
 *
 * **Le probleme, qui n'est pas celui des telephones.** Sur un mobile, le `dp` fait
 * son travail : il normalise la taille apparente, parce que la distance de lecture
 * suit la taille de l'ecran. Devant un televiseur, la distance ne change pas — on
 * reste dans son canape — mais le nombre de `dp` declare par un meme panneau varie
 * du simple au double :
 *
 * | Panneau | Densite | Largeur declaree |
 * | --- | --- | --- |
 * | 1920 x 1080 | 320 dpi | **960 dp** |
 * | 1920 x 1080 | 213 dpi | ~1442 dp |
 * | 1920 x 1080 | 160 dpi | **1920 dp** |
 * | 3840 x 2160 | 640 dpi | 960 dp |
 *
 * Une mise en page ecrite en `dp` fixes et reglee sur la premiere ligne s'affiche
 * donc **deux fois plus petite** sur la troisieme, alors que c'est le meme ecran a
 * la meme distance.
 *
 * **La reponse** : ne rien ecrire en `dp` absolus dans les ecrans, et tout deriver
 * d'un facteur calcule a l'execution, rapporte a une reference de [LARGEUR_REFERENCE]
 * par [HAUTEUR_REFERENCE]. L'interface garde alors exactement les memes proportions,
 * la meme densite d'information et la meme taille apparente, quel que soit ce que
 * l'appareil declare.
 *
 * C'est volontairement une **homothetie** et non des points de rupture. Un telephone
 * qui s'agrandit doit montrer *plus* de contenu, parce qu'on le rapproche ; un
 * televiseur, non : on le regarde de la meme distance, et y entasser plus de vignettes
 * ne ferait que les rendre illisibles.
 */
data class Gabarit(

    /** Rapport a la reference. 1 sur un ecran de 960 dp de large. */
    val facteur: Float,

    val largeurEcran: Dp,
    val hauteurEcran: Dp,

    // --- Marges et intervalles ---------------------------------------------

    /** Marge entre le rail et le contenu. */
    val margeDebut: Dp,

    /** Marge a droite de l'ecran. */
    val margeFin: Dp,

    /** Intervalle entre deux cartes d'une meme rangee. */
    val espacementCarte: Dp,

    /** Intervalle entre deux rangees. */
    val espacementSection: Dp,

    // --- Barre laterale -----------------------------------------------------

    val largeurRail: Dp,
    val largeurRailDeploye: Dp,
    val hauteurEntreeRail: Dp,
    val tailleIconeRail: Dp,

    // --- Cartes -------------------------------------------------------------

    /** Affiche portrait d'une rangee d'accueil. */
    val largeurAffiche: Dp,

    /** Affiche portrait d'une grille (catalogue filtre, resultats de recherche). */
    val largeurAfficheGrille: Dp,

    /** Vignette paysage d'un episode. */
    val largeurVignetteEpisode: Dp,

    // --- Accueil ------------------------------------------------------------

    val hauteurBanniere: Dp,

    // --- Fiche --------------------------------------------------------------

    val hauteurEnteteFiche: Dp,
    val hauteurRangeeEpisodes: Dp,
    val hauteurDetails: Dp,

    // --- Clavier ------------------------------------------------------------

    /**
     * Cote d'une touche du clavier virtuel.
     *
     * Regle pour que la grille occupe environ **le quart** de la largeur, comme sur
     * les grandes plateformes : le clavier est un moyen, les resultats sont la fin.
     * Une touche plus grande le fait deborder sur la moitie de l'ecran et chasse les
     * affiches qu'on est en train de filtrer.
     */
    val coteTouche: Dp,
    val espacementTouche: Dp,
    val hauteurChampSaisie: Dp
) {

    /** Largeur totale du clavier : six touches et leurs intervalles. */
    val largeurClavier: Dp get() = coteTouche * COLONNES_CLAVIER +
        espacementTouche * (COLONNES_CLAVIER - 1)

    /** Largeur d'une touche d'action : deux colonnes. */
    val largeurToucheAction: Dp get() = coteTouche * 2 + espacementTouche

    /**
     * Nombre de cartes qui tiennent dans [largeurDisponible].
     *
     * Le calcul reste utile malgre l'homothetie : un televiseur au rapport inhabituel
     * — 21/9, ou une dalle que le systeme declare de travers — n'a pas la largeur que
     * le facteur suppose. Compter au lieu de figer evite alors une derniere colonne
     * coupee net par le bord de l'ecran.
     *
     * @param reserve part de la largeur d'une carte laissee libre pour absorber
     *   l'agrandissement au focus.
     */
    fun colonnes(
        largeurDisponible: Dp,
        largeurCarte: Dp,
        reserve: Float = RESERVE_FOCUS
    ): Int {
        val pas = largeurCarte + espacementCarte
        if (pas <= 0.dp) return 1

        // `n` cartes occupent `n * pas - espacementCarte` : il n'y a d'intervalle
        // **qu'entre** elles, pas apres la derniere. Oublier ce terme fait perdre une
        // colonne des que la place restante est juste suffisante.
        val utile = largeurDisponible - largeurCarte * reserve + espacementCarte
        return floor(utile / pas).toInt().coerceAtLeast(1)
    }

    /**
     * Colonnes d'une grille occupant toute la largeur de l'ecran.
     *
     * C'est le cas des onglets d'une fiche — suggestions, personnages, staff — qui
     * s'affichent hors du cadre a barre laterale et n'ont donc pas de rail a deduire.
     */
    fun colonnesFiche(largeurCarte: Dp): Int =
        colonnes(largeurEcran - margeFin * 2, largeurCarte)

    companion object {

        /**
         * Reference de calibrage : 1920 x 1080 a 320 dpi, la definition la plus
         * repandue sur les boitiers Android TV. Toutes les valeurs ci-dessus sont les
         * mesures reglees sur cet ecran ; ailleurs, elles sont mises a l'echelle.
         */
        val LARGEUR_REFERENCE: Dp = 960.dp
        val HAUTEUR_REFERENCE: Dp = 540.dp

        /** Six colonnes de touches, quelle que soit la taille de l'ecran. */
        const val COLONNES_CLAVIER = 6

        /** Part d'une carte gardee libre pour l'agrandissement au focus. */
        private const val RESERVE_FOCUS = 0.25f

        /**
         * Bornes du facteur.
         *
         * En dessous, le texte deviendrait illisible a trois metres ; au-dela, une
         * seule rangee occuperait l'ecran. Aucun televiseur courant n'en sort, mais
         * une dalle mal declaree, si — et l'interface doit rester utilisable.
         */
        private const val FACTEUR_MIN = 0.8f
        private const val FACTEUR_MAX = 2.2f

        /**
         * Gabarit d'un ecran donne. Fonction pure, donc verifiable hors appareil.
         *
         * Le facteur suit la **plus contraignante** des deux dimensions : se fier a
         * la seule largeur ferait deborder verticalement une dalle plus haute que le
         * 16/9 de reference.
         */
        fun pour(largeur: Dp, hauteur: Dp): Gabarit {
            val facteur = min(
                largeur / LARGEUR_REFERENCE,
                hauteur / HAUTEUR_REFERENCE
            ).coerceIn(FACTEUR_MIN, FACTEUR_MAX)

            fun mise(reference: Int): Dp = (reference * facteur).dp

            return Gabarit(
                facteur = facteur,
                largeurEcran = largeur,
                hauteurEcran = hauteur,

                margeDebut = mise(28),
                margeFin = mise(48),
                espacementCarte = mise(20),
                espacementSection = mise(24),

                largeurRail = mise(76),
                largeurRailDeploye = mise(248),
                hauteurEntreeRail = mise(48),
                tailleIconeRail = mise(24),

                largeurAffiche = mise(132),
                largeurAfficheGrille = mise(146),
                largeurVignetteEpisode = mise(260),

                hauteurBanniere = mise(250),

                hauteurEnteteFiche = mise(340),
                hauteurRangeeEpisodes = mise(300),
                hauteurDetails = mise(430),

                coteTouche = mise(34),
                espacementTouche = mise(6),
                hauteurChampSaisie = mise(52)
            )
        }
    }
}

/**
 * Gabarit courant.
 *
 * `staticCompositionLocalOf` et non `compositionLocalOf` : il ne change qu'au
 * changement d'ecran, evenement rarissime sur un televiseur. Inutile de faire suivre
 * a Compose les lectures une par une.
 */
val LocalGabarit = staticCompositionLocalOf {
    Gabarit.pour(Gabarit.LARGEUR_REFERENCE, Gabarit.HAUTEUR_REFERENCE)
}

/** Gabarit deduit de la configuration en cours. */
@Composable
fun gabaritCourant(): Gabarit {
    val configuration = LocalConfiguration.current
    val largeur = configuration.screenWidthDp.dp
    val hauteur = configuration.screenHeightDp.dp

    return remember(largeur, hauteur) { Gabarit.pour(largeur, hauteur) }
}
