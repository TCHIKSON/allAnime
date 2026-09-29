package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.Slugifier
import com.allanime.tv.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Verification de bout en bout du port des scripts Node, contre le vrai catalogue.
 *
 * Ce test sort du perimetre des tests unitaires : il depend du reseau et de l'etat
 * du site, donc il est ignore par defaut. Retirer l'annotation `@Ignore` pour
 * controler que le scraping fonctionne toujours apres une evolution du catalogue :
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*CatalogueIntegrationTest"
 */
@Ignore("Depend du reseau et de l'etat du catalogue ; a lancer manuellement.")
class CatalogueIntegrationTest {

    private val episodesScraper = EpisodesScraper(HttpClients.shared)
    private val saisonsScraper = SaisonsScraper(HttpClients.shared, episodesScraper)

    @Test
    fun `les saisons d un anime connu sont retrouvees avec leurs langues`() = runBlocking {
        val slug = Slugifier.slugify("Classroom of the Elite")
        val saisons = saisonsScraper.fetch(AppConfig.catalogueUrl(slug))

        assertEquals("classroom-of-the-elite", slug)
        assertTrue("Au moins une saison attendue", saisons.isNotEmpty())

        saisons.forEach { saison ->
            println(
                "${saison.nom} | ${saison.nombreEpisodes} episodes | " +
                    "${saison.languesResume} | ${saison.baseUrl}"
            )
        }

        val premiere = saisons.first()
        assertEquals("Saison 1", premiere.nom)
        assertTrue("VOSTFR attendu", premiere.languesResume.contains("VOSTFR"))
        assertTrue("Nombre d'episodes attendu", (premiere.nombreEpisodes ?: 0) > 0)
    }

    @Test
    fun `les episodes d une saison exposent leurs lecteurs`() = runBlocking {
        val slug = Slugifier.slugify("Classroom of the Elite")
        val episodes = episodesScraper.fetch("${AppConfig.catalogueUrl(slug)}saison1/vostfr")

        assertTrue(episodes.isNotEmpty())
        episodes.take(3).forEach { episode ->
            println("Episode ${episode.numero} : ${episode.lecteurs.map { it.hostLabel }}")
            assertTrue(episode.estDisponible)
        }
    }
}
