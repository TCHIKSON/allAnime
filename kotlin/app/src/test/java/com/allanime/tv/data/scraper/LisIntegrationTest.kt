package com.allanime.tv.data.scraper

import com.allanime.tv.data.net.HttpClients
import com.allanime.tv.data.repository.CatalogueRepository
import com.allanime.tv.domain.model.Saison
import com.allanime.tv.domain.model.animeDeTest
import com.allanime.tv.domain.model.RechercheLis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Deroule la chaine complete sur le site de lecture : recherche, groupes, episodes,
 * lecteurs.
 *
 * Depend du reseau et du secret `BASE_URL_LIS` : ignore par defaut, a lancer
 * manuellement apres une evolution du site ou du scraper.
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*LisIntegrationTest"
 */
@Ignore("Depend du reseau et du site de lecture ; a lancer manuellement.")
class LisIntegrationTest {

    private val scraper = LisScraper(HttpClients.shared)

    @Test
    fun `un titre mene jusqu'aux lecteurs de son premier episode`() = runBlocking {
        val fiche = scraper.rechercher("Clevatess").first()
        println("Fiche : ${fiche.titre} (${fiche.slug})")

        val groupes = scraper.groupes(scraper.urlFiche(fiche.slug))
        println("Groupes : " + groupes.map { "${it.titre} [${it.nombreEpisodes}] ${it.langues}" })
        assertTrue(groupes.isNotEmpty())
        assertTrue(groupes.all { it.langues.isNotEmpty() })

        val episodes = scraper.episodes(groupes.first().url)
        println("Episodes : " + episodes.map { it.numero })
        assertTrue(episodes.isNotEmpty())

        val lecteurs = scraper.lecteurs(episodes.first().url)
        println("Lecteurs : " + lecteurs.mapValues { it.value.size })
        assertTrue(lecteurs.values.any { it.isNotEmpty() })
    }

    @Test
    fun `les titres d'une fiche menent a la bonne serie`() = runBlocking {
        // Titres tels qu'AniList ou la carte du catalogue les donnent, et le slug
        // releve sur le site de lecture.
        val attendus = listOf(
            listOf("Sousou no Frieren", "Frieren: Beyond Journey's End") to "frieren",
            listOf("Boku no Hero Academia 7", "My Hero Academia Season 7") to "my-hero-academia-6",
            listOf("Shingeki no Kyojin", "L'Attaque des Titans") to "lattaque-des-titans",
            listOf("Re:ZERO -Starting Life in Another World-") to "rezero-starting-life-in-another-world-4",
            listOf("Kimetsu no Yaiba") to "demon-slayer",
            listOf("ONE PIECE") to "one-piece"
        )

        attendus.forEach { (titres, attendu) ->
            val slug = RechercheLis.trouver(titres, maxTitres = 8, scraper::rechercher)?.slug
            println("$titres -> $slug")
            assertEquals(titres.toString(), attendu, slug)
        }
    }

    private fun depot(): CatalogueRepository {
        val episodes = EpisodesScraper(HttpClients.shared)
        return CatalogueRepository(
            saisonsScraper = SaisonsScraper(HttpClients.shared, episodes),
            episodesScraper = episodes,
            slugResolver = SlugResolver(
                HttpClients.shared,
                RechercheCatalogueScraper(HttpClients.shared)
            ),
            lisScraper = scraper
        )
    }

    private val clevatess = animeDeTest(
        romaji = "Clevatess: Majuu no Ou to Akago to Shikabane no Yuusha",
        english = "Clevatess"
    )

    /** Chaque saison doit mener jusqu'aux lecteurs de son premier episode. */
    private suspend fun verifier(depot: CatalogueRepository, saisons: List<Saison>) {
        assertTrue(saisons.isNotEmpty())
        saisons.forEach { saison ->
            println("Saison : ${saison.nom} [${saison.nombreEpisodes}] ${saison.path}")
            val langue = requireNotNull(saison.langueParDefaut)
            val premier = depot.episodes(clevatess, langue).first { it.estDisponible }
            val pret = depot.lecteurs(langue, premier)
            println("  ep. ${pret.numero} : ${pret.lecteurs.map { it.hostLabel }}")
            assertTrue(pret.lecteurs.isNotEmpty())
        }
    }

    @Test
    fun `les deux methodes chargent ensemble les saisons d'une fiche`() = runBlocking {
        val depot = depot()

        verifier(depot, depot.saisons(clevatess))
    }

    @Test
    fun `le site de lecture suffit quand le catalogue ne connait pas la serie`() = runBlocking {
        val depot = depot()
        // Un slug que le catalogue n'a pas : la methode d'origine echoue, et seule
        // la methode ajoutee peut encore rendre la serie lisible.
        depot.associer(clevatess.id, "slug-que-le-catalogue-n-a-pas")

        val saisons = depot.saisons(clevatess)

        assertTrue(saisons.all { it.path.startsWith("lis:") })
        verifier(depot, saisons)
    }
}
