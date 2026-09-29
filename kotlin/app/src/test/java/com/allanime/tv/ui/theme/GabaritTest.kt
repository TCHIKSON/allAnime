package com.allanime.tv.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mise a l'echelle de l'interface selon le televiseur.
 *
 * Les cas testes sont de vraies configurations d'Android TV, pas des nombres
 * inventes : c'est tout l'interet de la classe, et la raison pour laquelle une
 * mise en page en `dp` fixes ne suffit pas.
 */
class GabaritTest {

    @Test
    fun `l ecran de reference donne un facteur de un`() {
        // 1920 x 1080 a 320 dpi : le boitier le plus repandu, et celui sur lequel
        // toutes les valeurs de reference ont ete reglees.
        val gabarit = Gabarit.pour(960.dp, 540.dp)

        assertEquals(1f, gabarit.facteur, 0.001f)
        assertEquals(132.dp, gabarit.largeurAffiche)
        assertEquals(76.dp, gabarit.largeurRail)
        assertEquals(34.dp, gabarit.coteTouche)
    }

    @Test
    fun `une dalle identique en 160 dpi double les dimensions`() {
        // Meme panneau 1920 x 1080, meme distance de vision, mais declare en
        // 1920 x 1080 dp. Sans mise a l'echelle, tout s'y afficherait deux fois
        // plus petit — c'est precisement le defaut que le gabarit corrige.
        val gabarit = Gabarit.pour(1920.dp, 1080.dp)

        assertEquals(2f, gabarit.facteur, 0.001f)
        assertEquals(264.dp, gabarit.largeurAffiche)
        assertEquals(152.dp, gabarit.largeurRail)
    }

    @Test
    fun `le 720p en 213 dpi reste proche de la reference`() {
        val gabarit = Gabarit.pour(961.dp, 541.dp)

        assertTrue(
            "facteur attendu proche de 1, obtenu ${gabarit.facteur}",
            gabarit.facteur in 0.99f..1.02f
        )
    }

    @Test
    fun `le facteur suit la dimension la plus contraignante`() {
        // Un ecran large mais peu haut ne doit pas grandir sur sa seule largeur :
        // le contenu deborderait verticalement.
        val gabarit = Gabarit.pour(1920.dp, 540.dp)

        assertEquals(1f, gabarit.facteur, 0.001f)
    }

    @Test
    fun `le facteur reste borne sur une dalle mal declaree`() {
        val minuscule = Gabarit.pour(320.dp, 180.dp)
        val enorme = Gabarit.pour(6000.dp, 3400.dp)

        assertEquals(0.8f, minuscule.facteur, 0.001f)
        assertEquals(2.2f, enorme.facteur, 0.001f)
    }

    @Test
    fun `les colonnes se comptent sur la largeur disponible`() {
        val gabarit = Gabarit.pour(960.dp, 540.dp)

        // 5 cartes de 146 dp et leurs intervalles de 20 dp tiennent dans 850 dp,
        // reserve de focus comprise.
        assertEquals(5, gabarit.colonnes(850.dp, 146.dp))
        assertEquals(2, gabarit.colonnes(400.dp, 146.dp))
    }

    @Test
    fun `une largeur derisoire laisse toujours une colonne`() {
        val gabarit = Gabarit.pour(960.dp, 540.dp)

        // Mieux vaut une carte coupee qu'une grille vide : l'ecran reste parcourable.
        assertEquals(1, gabarit.colonnes(10.dp, 146.dp))
    }

    @Test
    fun `le nombre de colonnes ne change pas avec la taille de l ecran`() {
        // Homothetie : un televiseur plus grand se regarde de la meme distance, il
        // doit donc montrer **autant** de vignettes, pas davantage.
        val reference = Gabarit.pour(960.dp, 540.dp)
        val grand = Gabarit.pour(1920.dp, 1080.dp)

        assertEquals(
            reference.colonnes(
                reference.largeurEcran - reference.margeFin * 2,
                reference.largeurAfficheGrille
            ),
            grand.colonnes(
                grand.largeurEcran - grand.margeFin * 2,
                grand.largeurAfficheGrille
            )
        )
    }

    @Test
    fun `le clavier occupe environ le quart de l ecran`() {
        val gabarit = Gabarit.pour(960.dp, 540.dp)

        // Le clavier est un moyen, les resultats sont la fin : il ne doit pas
        // deborder sur la moitie de l'ecran et chasser les affiches qu'on filtre.
        val part = gabarit.largeurClavier / gabarit.largeurEcran
        assertTrue("part occupee : ${'$'}part", part in 0.20f..0.28f)
    }

    @Test
    fun `la largeur du clavier suit ses touches`() {
        val gabarit = Gabarit.pour(960.dp, 540.dp)

        // Six touches et cinq intervalles.
        assertEquals(34.dp * 6 + 6.dp * 5, gabarit.largeurClavier)
        assertEquals(34.dp * 2 + 6.dp, gabarit.largeurToucheAction)
    }
}
