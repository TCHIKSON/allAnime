package com.allanime.tv.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Flou applique aux vignettes d'episodes pas encore commences.
 *
 * **Pourquoi ne pas employer `Modifier.blur`** : il repose sur `RenderEffect`, qui
 * n'existe qu'a partir d'Android 12. En dessous, l'appel ne leve pas — il ne fait
 * simplement rien, et la vignette s'afficherait nette sur la majorite des
 * televiseurs, y compris celui vise ici. Le flou doit donc etre applique a l'image
 * elle-meme, au moment ou elle est decodee.
 *
 * **Comment** : l'image est reduite a [FACTEUR] fois moins que sa taille, puis
 * ramenee a ses dimensions d'origine **par paliers**. Une seule remontee depuis une
 * miniature ne fond pas les pixels, elle les etire : on obtient une mosaique de
 * carres, pas un flou. En doublant la taille a chaque palier, chaque interpolation
 * bilineaire ne travaille qu'entre pixels voisins, et leurs effets se composent en
 * un degrade continu — le rendu d'un flou gaussien, pour une fraction de son cout.
 *
 * Le resultat sert a **ne pas divulguer** ce qui se passe dans un episode qu'on n'a
 * pas encore vu : la vignette dit qu'il y a une image, pas laquelle.
 */
class FlouTransformation(
    private val facteur: Int = FACTEUR
) : Transformation {

    /**
     * La clef de cache porte le facteur : deux intensites de flou ne doivent pas se
     * recouvrir dans le cache disque de Coil, et une vignette floutee ne doit jamais
     * ressortir a la place de la meme vignette nette.
     */
    override val cacheKey: String = "${FlouTransformation::class.java.name}-$facteur"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val largeur = input.width
        val hauteur = input.height
        if (largeur <= 0 || hauteur <= 0) return input

        // Au moins un pixel dans chaque dimension : une image deja minuscule ne doit
        // pas se reduire a rien.
        val largeurReduite = max(1, (largeur.toFloat() / facteur).roundToInt())
        val hauteurReduite = max(1, (hauteur.toFloat() / facteur).roundToInt())

        var courante = Bitmap.createScaledBitmap(input, largeurReduite, hauteurReduite, true)

        // Remontee par doublements successifs, jusqu'a retrouver la taille d'origine.
        while (courante.width < largeur || courante.height < hauteur) {
            val prochaineLargeur = (courante.width * 2).coerceAtMost(largeur)
            val prochaineHauteur = (courante.height * 2).coerceAtMost(hauteur)

            val agrandie = Bitmap.createScaledBitmap(
                courante,
                prochaineLargeur,
                prochaineHauteur,
                true
            )
            // `createScaledBitmap` rend la source telle quelle quand les dimensions
            // coincident deja : ne recycler que ce qui est bien un intermediaire.
            if (agrandie != courante && courante != input) courante.recycle()
            courante = agrandie
        }

        return courante
    }

    private companion object {
        /**
         * Assez fort pour qu'aucun visage ni aucun texte ne reste lisible, assez
         * doux pour que les couleurs dominantes de la scene restent reconnaissables.
         */
        const val FACTEUR = 16
    }
}
