package com.allanime.tv.domain.model

import com.allanime.tv.data.repository.CatalogueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Retrouver une serie sur le site de lecture a partir d'un titre de sa fiche.
 *
 * Les titres et les reponses sont ceux releves sur le site : son moteur ne rend une
 * fiche que si la requete figure telle quelle dans le titre qu'il lui donne.
 */
class RechercheLisTest {

    private fun fiche(slug: String, titre: String) =
        ResultatRecherche(slug = slug, titre = titre, sousTitres = emptyList(), image = null)

    @Test
    fun `le titre entier est toujours la premiere requete`() {
        assertEquals("Clevatess", RechercheLis.requetes("Clevatess").first())
        assertEquals(
            "Frieren: Beyond Journey's End",
            RechercheLis.requetes("Frieren: Beyond Journey's End").first()
        )
    }

    @Test
    fun `la partie avant le sous-titre est proposee`() {
        val requetes = RechercheLis.requetes("Frieren: Beyond Journey's End")

        assertEquals(1, requetes.indexOf("Frieren"))
    }

    @Test
    fun `un marqueur de suite et la ponctuation des bords sont retires`() {
        assertEquals(
            "My Hero Academia",
            RechercheLis.requetes("My Hero Academia Season 7")[1]
        )
        assertEquals(
            "Re:ZERO -Starting Life in Another World",
            RechercheLis.requetes("Re:ZERO -Starting Life in Another World-")[1]
        )
    }

    @Test
    fun `les mots les plus longs servent de dernier recours`() {
        val requetes = RechercheLis.requetes("L'Attaque des Titans")

        assertEquals(listOf("Attaque", "Titans"), requetes.takeLast(2))
    }

    @Test
    fun `une meme requete n'est pas posee deux fois`() {
        val requetes = RechercheLis.requetes("Frieren")

        assertEquals(listOf("Frieren"), requetes)
    }

    @Test
    fun `la fiche au titre exact l'emporte sur ses derivees`() {
        val resultats = listOf(
            fiche("my-hero-academia-youre-next-1", "My Hero Academia: You’re Next"),
            fiche("my-hero-academia-vigilantes-3", "My Hero Academia: Vigilantes"),
            fiche("my-hero-academia-6", "My Hero Academia")
        )

        assertEquals(
            "my-hero-academia-6",
            RechercheLis.exacte("My Hero Academia Season 7", resultats)?.slug
        )
    }

    @Test
    fun `un titre ecrit sans apostrophe par le site est reconnu`() {
        val resultats = listOf(
            fiche("lattaque-des-titans-la-derniere-attaque", "L’Attaque des titans : La dernière attaque"),
            fiche("lattaque-des-titans", "LAttaque des Titans"),
            fiche("teen-titans-go", "Teen Titans Go!")
        )

        assertEquals(
            "lattaque-des-titans",
            RechercheLis.exacte("L'Attaque des Titans", resultats)?.slug
        )
    }

    @Test
    fun `le site nomme parfois la serie par son seul debut`() {
        val resultats = listOf(fiche("frieren", "Frieren"))

        assertEquals(
            "frieren",
            RechercheLis.exacte("Frieren: Beyond Journey's End", resultats)?.slug
        )
    }

    @Test
    fun `un resultat sans rapport n'est jamais retenu`() {
        val resultats = listOf(
            fiche("pokemon-le-film-mewtwo-contre-attaque", "Pokémon, le film: Mewtwo contre-attaque"),
            fiche("dragon-ball-z-attaque-super-warrior", "Dragon Ball Z - Attaque Super Warrior !")
        )

        assertNull(RechercheLis.exacte("L'Attaque des Titans", resultats))
        assertNull(RechercheLis.approchee("L'Attaque des Titans", resultats))
        assertNull(RechercheLis.exacte("Shingeki no Kyojin", emptyList()))
    }

    @Test
    fun `une fiche approchee attend que toutes les requetes soient posees`() = runBlocking {
        // Releve sur le site : la premiere requete ne rend qu'une serie derivee, la
        // serie elle-meme ne sort qu'au mot-clef.
        val site = mapOf(
            "l'attaque des titans" to listOf(
                fiche(
                    "lattaque-des-titans-junior-high-school",
                    "L'Attaque des Titans - Junior High School"
                )
            ),
            "titans" to listOf(
                fiche(
                    "lattaque-des-titans-la-derniere-attaque",
                    "L’Attaque des titans : La dernière attaque"
                ),
                fiche("lattaque-des-titans", "LAttaque des Titans")
            )
        )
        val posees = mutableListOf<String>()

        val trouvee = RechercheLis.trouver(
            titres = listOf("Shingeki no Kyojin", "L'Attaque des Titans"),
            maxTitres = 8
        ) { requete ->
            posees += requete.lowercase()
            site[requete.lowercase()].orEmpty()
        }

        assertEquals("lattaque-des-titans", trouvee?.slug)
        // Aucune requete n'est envoyee deux fois au site.
        assertEquals(posees.distinct(), posees)
    }

    @Test
    fun `a defaut de titre exact la fiche approchee est retenue`() = runBlocking {
        val trouvee = RechercheLis.trouver(listOf("Kimetsu no Yaiba"), maxTitres = 8) { requete ->
            if (requete == "Kimetsu no Yaiba") {
                listOf(fiche("demon-slayer", "Demon Slayer : Kimetsu no Yaiba"))
            } else {
                emptyList()
            }
        }

        assertEquals("demon-slayer", trouvee?.slug)
    }

    @Test
    fun `le debut d'un titre ne designe pas la premiere serie qui commence pareil`() = runBlocking {
        // Releve sur le site : « The Last » rend d'abord « The Last Summoner », une
        // serie de douze episodes, et seulement ensuite le film cherche.
        val site = mapOf(
            "the last" to listOf(
                fiche("the-last-summoner", "The Last Summoner"),
                fiche("hunter-x-hunter-the-last-mission-1", "Hunter X Hunter - The Last Mission"),
                fiche("naruto-the-last-le-film", "Naruto the Last: Le film")
            ),
            "naruto" to listOf(
                fiche("naruto", "Naruto"),
                fiche("naruto-shippuden", "Naruto Shippuden")
            )
        )

        val trouvee = RechercheLis.trouver(
            titres = listOf("The Last: Naruto the Movie", "THE LAST: NARUTO THE MOVIE"),
            maxTitres = 8
        ) { requete -> site[requete.lowercase()].orEmpty() }

        assertEquals("naruto-the-last-le-film", trouvee?.slug)
    }

    @Test
    fun `un titre abrege n'est reconnu qu'a l'identique`() {
        val resultats = listOf(
            fiche("the-last-summoner", "The Last Summoner"),
            fiche("izetta-the-last-witch", "Izetta: The Last Witch")
        )

        assertNull(CorrespondanceCatalogue.choisirExact("THE LAST", resultats))
        assertEquals(
            "frieren",
            CorrespondanceCatalogue.choisirExact("Frieren", listOf(fiche("frieren", "Frieren")))?.slug
        )
    }

    @Test
    fun `les titres complets d'un anime n'ont pas de forme raccourcie`() {
        val film = animeDeTest(romaji = "THE LAST: NARUTO THE MOVIE", english = "The Last: Naruto the Movie")

        assertEquals(
            listOf("THE LAST: NARUTO THE MOVIE", "The Last: Naruto the Movie"),
            film.titresComplets
        )
        assertEquals(true, "THE LAST" in film.candidatsCatalogue)
    }

    @Test
    fun `aucun titre connu du site ne rend aucune fiche`() = runBlocking {
        val trouvee = RechercheLis.trouver(listOf("Shingeki no Kyojin"), maxTitres = 8) {
            emptyList()
        }

        assertNull(trouvee)
    }

    @Test
    fun `une saison se reconnait d'un site a l'autre par son type et son numero`() {
        assertEquals(CatalogueRepository.cle("Saison 1"), CatalogueRepository.cle("saison 1"))
        assertEquals(
            CatalogueRepository.cle("Saga 1 (East Blue)"),
            CatalogueRepository.cle("Saga 1 East Blue [Episode 1 à 61]")
        )
        assertEquals(CatalogueRepository.cle("Film"), CatalogueRepository.cle("Films"))

        assertNotEquals(CatalogueRepository.cle("Saison 1"), CatalogueRepository.cle("Saison 2"))
        assertNotEquals(CatalogueRepository.cle("Saison 1"), CatalogueRepository.cle("Saison 10"))
        assertNotEquals(
            CatalogueRepository.cle("Saga 1 (East Blue)"),
            CatalogueRepository.cle("Kai - Saga 1 (East Blue)")
        )
        assertNotEquals(CatalogueRepository.cle("Film"), CatalogueRepository.cle("OAV"))
    }
}
