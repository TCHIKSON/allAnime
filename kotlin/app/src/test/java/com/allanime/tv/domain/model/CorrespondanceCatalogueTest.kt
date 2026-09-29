package com.allanime.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Choix de la fiche du catalogue parmi les reponses du moteur de recherche.
 *
 * Deux exigences opposees : reconnaitre une serie sous n'importe lequel de ses noms,
 * et ne rien retenir quand rien ne correspond — le sondage de slugs prenant alors la
 * suite. Un mauvais choix est pire qu'aucun : il menerait aux saisons d'une autre
 * serie.
 */
class CorrespondanceCatalogueTest {

    private fun entree(slug: String, titre: String, vararg sousTitres: String) =
        ResultatRecherche(slug = slug, titre = titre, sousTitres = sousTitres.toList(), image = null)

    private val reponse = listOf(
        entree("no-6", "No.6", "No. 6"),
        entree(
            "shingeki-no-kyojin",
            "Attack on Titan",
            "Shingeki no Kyojin",
            "L'Attaque des Titans"
        ),
        entree("oshi-no-ko", "Oshi No Ko", "\"Oshi no Ko\"", "My Favorite Idol's Children")
    )

    @Test
    fun `le titre principal est reconnu`() {
        assertEquals("no-6", CorrespondanceCatalogue.choisir("No.6", reponse)?.slug)
    }

    @Test
    fun `un titre alternatif est reconnu`() {
        assertEquals(
            "shingeki-no-kyojin",
            CorrespondanceCatalogue.choisir("Shingeki no Kyojin", reponse)?.slug
        )
    }

    @Test
    fun `les accents et la ponctuation ne font pas echouer la comparaison`() {
        assertEquals(
            "shingeki-no-kyojin",
            CorrespondanceCatalogue.choisir("L'Attaque des Titans", reponse)?.slug
        )
        assertEquals("no-6", CorrespondanceCatalogue.choisir("No 6", reponse)?.slug)
    }

    @Test
    fun `une suite retombe sur la fiche de la serie`() {
        // Le catalogue regroupe toutes les saisons sous une seule fiche : le titre
        // complet n'y figure jamais tel quel.
        listOf(
            "Shingeki no Kyojin Season 3",
            "Shingeki no Kyojin Season 3 Part 2",
            "Shingeki no Kyojin: The Final Season",
            "Shingeki no Kyojin II",
            "Attack on Titan 2"
        ).forEach { titre ->
            assertEquals(titre, "shingeki-no-kyojin", CorrespondanceCatalogue.choisir(titre, reponse)?.slug)
        }
    }

    @Test
    fun `un titre sans rapport ne retient rien`() {
        assertNull(CorrespondanceCatalogue.choisir("Cowboy Bebop", reponse))
        assertNull(CorrespondanceCatalogue.choisir("Cowboy Bebop Saison 2", reponse))
    }

    @Test
    fun `une reponse vide ou un titre vide ne retiennent rien`() {
        assertNull(CorrespondanceCatalogue.choisir("No.6", emptyList()))
        assertNull(CorrespondanceCatalogue.choisir("", reponse))
        assertNull(CorrespondanceCatalogue.choisir("!!!", reponse))
    }

    @Test
    fun `le titre principal l emporte sur une simple presence`() {
        val ambigu = listOf(
            entree("one-piece-film-red", "One Piece Film: Red"),
            entree("one-piece", "One Piece")
        )

        assertEquals("one-piece", CorrespondanceCatalogue.choisir("One Piece", ambigu)?.slug)
    }

    @Test
    fun `un titre coupe par le decoupage des sous-titres reste reconnaissable`() {
        // Le site separe les titres alternatifs par des virgules, dont certains
        // titres contiennent : le sous-titre recompose rattrape le decoupage.
        val clevatess = listOf(
            entree(
                "clevatess",
                "Clevatess",
                "Clevatess: The King of Devil Beasts",
                "The Baby and the Brave of Undead"
            )
        )

        assertEquals(
            "clevatess",
            CorrespondanceCatalogue.choisir(
                "Clevatess: The King of Devil Beasts, The Baby and the Brave of Undead",
                clevatess
            )?.slug
        )
    }

    @Test
    fun `les marqueurs de suite sont retires un par un`() {
        assertEquals(
            "Shingeki no Kyojin",
            CorrespondanceCatalogue.sansMarqueurDeSuite("Shingeki no Kyojin Season 3 Part 2")
        )
        assertEquals(
            "Overlord",
            CorrespondanceCatalogue.sansMarqueurDeSuite("Overlord IV")
        )
        assertEquals(
            "Mushoku Tensei",
            CorrespondanceCatalogue.sansMarqueurDeSuite("Mushoku Tensei - Saison 2")
        )
    }

    @Test
    fun `un titre sans marqueur n est pas raccourci`() {
        listOf("Cowboy Bebop", "One Piece", "Kimi no Na wa").forEach { titre ->
            assertEquals(titre, CorrespondanceCatalogue.sansMarqueurDeSuite(titre))
        }
    }

    @Test
    fun `un titre entierement fait de marqueurs reste intact`() {
        // Rendre une chaine vide ferait correspondre n'importe quelle fiche.
        assertEquals("Saison 2", CorrespondanceCatalogue.sansMarqueurDeSuite("Saison 2"))
    }
}
