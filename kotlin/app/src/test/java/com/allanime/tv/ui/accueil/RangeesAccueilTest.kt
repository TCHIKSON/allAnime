package com.allanime.tv.ui.accueil

import com.allanime.tv.domain.model.catalogueDeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rangees thematiques de l'accueil. Elles sont deduites du catalogue deja telecharge
 * et de l'historique local : aucun appel reseau, donc entierement verifiable ici.
 *
 * La propriete centrale verifiee ici est l'absence de doublon : un anime etiquete
 * sous plusieurs genres ne doit figurer que dans une seule rangee de classement.
 */
class RangeesAccueilTest {

    /** Quatre animes par genre, c'est le minimum pour qu'une rangee existe. */
    private fun catalogueAction(nombre: Int) =
        (1..nombre).map { catalogueDeTest(slug = "action-$it", genres = listOf("Action")) }

    private fun genres(rangees: List<RangeeAccueil>) =
        rangees.filterIsInstance<RangeeAccueil.Genre>().map { it.genre }

    @Test
    fun `un genre assez represente donne une rangee`() {
        val rangees = RangeesAccueil.construire(catalogueAction(5))

        assertEquals(1, rangees.size)
        val genre = rangees.first() as RangeeAccueil.Genre
        assertEquals("Action", genre.genre)
        assertEquals(5, genre.animes.size)
    }

    @Test
    fun `un genre trop peu represente est ecarte`() {
        assertTrue(RangeesAccueil.construire(catalogueAction(3)).isEmpty())
    }

    @Test
    fun `les genres dominants passent devant`() {
        val catalogue = catalogueAction(6) +
            (1..5).map { catalogueDeTest(slug = "comedie-$it", genres = listOf("Comédie")) }

        assertEquals(listOf("Action", "Comédie"), genres(RangeesAccueil.construire(catalogue)))
    }

    @Test
    fun `un anime a plusieurs genres ne figure que dans une rangee`() {
        // Chaque titre porte les deux genres : le plus represente les prend tous, et
        // le second n'a plus de quoi exister.
        val catalogue = (1..6).map {
            catalogueDeTest(slug = "double-$it", genres = listOf("Action", "Comédie"))
        }

        val rangees = RangeesAccueil.construire(catalogue)

        assertEquals(listOf("Action"), genres(rangees))
        val apparitions = rangees.flatMap { it.animes }.map { it.slug }
        assertEquals(apparitions.size, apparitions.distinct().size)
    }

    @Test
    fun `le second genre garde ce que le premier n a pas pris`() {
        val communs = (1..4).map {
            catalogueDeTest(slug = "commun-$it", genres = listOf("Action", "Comédie"))
        }
        val actionSeule = (1..5).map {
            catalogueDeTest(slug = "action-$it", genres = listOf("Action"))
        }
        val comedieSeule = (1..4).map {
            catalogueDeTest(slug = "comedie-$it", genres = listOf("Comédie"))
        }

        val rangees = RangeesAccueil.construire(communs + actionSeule + comedieSeule)
            .filterIsInstance<RangeeAccueil.Genre>()

        // Action est le plus fourni (9 contre 8) et emporte les titres communs ;
        // Comédie garde ses quatre exclusifs, ce qui lui suffit encore pour exister.
        assertEquals(listOf("Action", "Comédie"), rangees.map { it.genre })
        assertEquals(9, rangees.first().animes.size)
        assertEquals(comedieSeule.map { it.slug }, rangees.last().animes.map { it.slug })
    }

    @Test
    fun `un genre vide par le classement ne bloque pas les suivants`() {
        // « Drame » est le deuxieme par effectif mais tous ses titres sont aussi
        // « Action » : sa rangee disparait, « Sport » prend sa place.
        val catalogue = (1..6).map {
            catalogueDeTest(slug = "a-$it", genres = listOf("Action", "Drame"))
        } + (1..5).map {
            catalogueDeTest(slug = "s-$it", genres = listOf("Sport"))
        }

        assertEquals(listOf("Action", "Sport"), genres(RangeesAccueil.construire(catalogue)))
    }

    @Test
    fun `le nombre de rangees de genre est plafonne`() {
        val catalogue = (1..RangeesAccueil.MAX_RANGEES_GENRE + 4).flatMap { rang ->
            (1..4).map {
                catalogueDeTest(slug = "g$rang-$it", genres = listOf("Genre%02d".format(rang)))
            }
        }

        val rangees = RangeesAccueil.construire(catalogue)
            .filterIsInstance<RangeeAccueil.Genre>()

        assertEquals(RangeesAccueil.MAX_RANGEES_GENRE, rangees.size)
    }

    @Test
    fun `les films sont classes par genre, sans rangee propre`() {
        // La barre laterale mene directement aux films : leur reserver une rangee ici
        // prendrait la place d'un genre sans rien apporter de plus.
        val films = (1..4).map {
            catalogueDeTest(
                slug = "film-$it",
                types = listOf("Anime", "Film"),
                genres = listOf("Drame")
            )
        }
        val series = (1..5).map {
            catalogueDeTest(slug = "serie-$it", genres = listOf("Drame"))
        }

        val rangees = RangeesAccueil.construire(films + series)

        assertEquals(listOf("Drame"), genres(rangees))
        // Les neuf titres, films compris, tiennent dans la seule rangee de genre.
        assertEquals(9, rangees.single().animes.size)
    }

    @Test
    fun `ce que le classement laisse de cote reste atteignable`() {
        val classes = (1..5).map { catalogueDeTest(slug = "action-$it", genres = listOf("Action")) }
        // Chacun a un genre unique : aucun n'atteint le seuil d'une rangee.
        val orphelins = (1..4).map {
            catalogueDeTest(slug = "rare-$it", genres = listOf("Rare$it"))
        }

        val rangees = RangeesAccueil.construire(classes + orphelins)

        val autres = rangees.filterIsInstance<RangeeAccueil.Autres>().single()
        assertEquals(orphelins.map { it.slug }, autres.animes.map { it.slug })
    }

    @Test
    fun `un reste trop maigre ne merite pas sa rangee`() {
        val catalogue = catalogueAction(5) +
            listOf(catalogueDeTest(slug = "seul", genres = listOf("Rare")))

        val rangees = RangeesAccueil.construire(catalogue)

        assertTrue(rangees.none { it is RangeeAccueil.Autres })
    }

    @Test
    fun `le voisinage du dernier anime ouvert ouvre la liste`() {
        val source = catalogueDeTest(
            slug = "source",
            titre = "Source",
            genres = listOf("Action", "Drame")
        )
        val catalogue = listOf(
            source,
            catalogueDeTest(slug = "tres-proche", genres = listOf("Action", "Drame")),
            catalogueDeTest(slug = "proche", genres = listOf("Action")),
            catalogueDeTest(slug = "proche-aussi", genres = listOf("Drame")),
            catalogueDeTest(slug = "proche-encore", genres = listOf("Action")),
            catalogueDeTest(slug = "hors-sujet", genres = listOf("Sport"))
        )

        val rangees = RangeesAccueil.construire(
            catalogue = catalogue,
            reference = source,
            dejaVus = setOf("source")
        )

        val similaires = rangees.first() as RangeeAccueil.Similaires
        assertEquals("Source", similaires.titreSource)
        // Le plus proche d'abord ; la source et le hors-sujet sont absents.
        assertEquals("tres-proche", similaires.animes.first().slug)
        assertFalse(similaires.animes.any { it.slug == "source" })
        assertFalse(similaires.animes.any { it.slug == "hors-sujet" })
    }

    @Test
    fun `la reference n apparait pas dans son propre voisinage`() {
        val catalogue = catalogueAction(6)

        val similaires = RangeesAccueil.construire(catalogue, reference = catalogue.first())
            .filterIsInstance<RangeeAccueil.Similaires>()
            .first()

        assertFalse(similaires.animes.any { it.slug == catalogue.first().slug })
    }

    @Test
    fun `les animes deja commences ne reviennent pas dans le voisinage`() {
        val catalogue = catalogueAction(6)

        val similaires = RangeesAccueil.construire(
            catalogue = catalogue,
            reference = catalogue.first(),
            dejaVus = setOf("action-1", "action-2")
        ).filterIsInstance<RangeeAccueil.Similaires>().first()

        assertEquals(
            listOf("action-3", "action-4", "action-5", "action-6"),
            similaires.animes.map { it.slug }
        )
    }

    @Test
    fun `un spectateur assidu garde sa rangee de similaires`() {
        // Presque tout a deja ete commence : exclure les deja-vus viderait la rangee.
        val catalogue = catalogueAction(6)

        val similaires = RangeesAccueil.construire(
            catalogue = catalogue,
            reference = catalogue.first(),
            dejaVus = setOf("action-1", "action-2", "action-3", "action-4", "action-5")
        ).filterIsInstance<RangeeAccueil.Similaires>().first()

        // La reference reste exclue, mais les autres deja-vus reviennent.
        assertEquals(
            listOf("action-2", "action-3", "action-4", "action-5", "action-6"),
            similaires.animes.map { it.slug }
        )
    }

    @Test
    fun `sans voisin suffisant aucune rangee de similaires`() {
        val source = catalogueDeTest(slug = "source", genres = listOf("Mecha"))
        val catalogue = listOf(source, catalogueDeTest(slug = "autre", genres = listOf("Mecha")))

        assertTrue(
            RangeesAccueil.construire(catalogue, reference = source)
                .filterIsInstance<RangeeAccueil.Similaires>()
                .isEmpty()
        )
    }

    @Test
    fun `une reference sans genre ne produit pas de voisinage`() {
        val rangees = RangeesAccueil.construire(
            catalogue = catalogueAction(5),
            reference = catalogueDeTest(slug = "sans-genre", genres = emptyList())
        )

        assertTrue(rangees.none { it is RangeeAccueil.Similaires })
    }

    @Test
    fun `sans reference il ne reste que le classement`() {
        val rangees = RangeesAccueil.construire(catalogueAction(5), reference = null)

        assertTrue(rangees.all { it is RangeeAccueil.Genre })
    }

    @Test
    fun `un catalogue vide ne produit aucune rangee`() {
        assertTrue(RangeesAccueil.construire(emptyList()).isEmpty())
    }

    @Test
    fun `les clefs des rangees sont distinctes`() {
        val catalogue = catalogueAction(6) +
            (1..5).map {
                catalogueDeTest(
                    slug = "mixte-$it",
                    genres = listOf("Comédie", "Action"),
                    types = listOf("Anime", "Film")
                )
            }

        val cles = RangeesAccueil.construire(catalogue, reference = catalogue.first())
            .map { it.cle }

        assertEquals(cles.size, cles.distinct().size)
    }
}
