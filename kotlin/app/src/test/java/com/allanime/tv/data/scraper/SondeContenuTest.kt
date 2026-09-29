package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

/**
 * Sonde : que contiennent vraiment les saisons d'une serie ?
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*SondeContenuTest"
 */
@Ignore("Depend du reseau et de l'etat du catalogue ; a lancer manuellement.")
class SondeContenuTest {

    private val episodesScraper = EpisodesScraper(HttpClients.shared)

    @Test
    fun `contenu saison par saison`() = runBlocking {
        val base = AppConfig.catalogueUrl("darwins-incident")

        listOf("saison1/vostfr", "saison2/vostfr", "saison3/vostfr", "saison4/vostfr").forEach { chemin ->
            val url = base + chemin
            println("\n##### $chemin")

            val episodes = runCatching { episodesScraper.fetch(url) }
            if (episodes.isFailure) {
                println("  echec : ${episodes.exceptionOrNull()?.message}")
                return@forEach
            }

            val liste = episodes.getOrThrow()
            println("  ${liste.size} episodes, dont ${liste.count { it.estDisponible }} exploitables")
            liste.take(3).forEach { episode ->
                println(
                    "  ep${episode.numero} : ${episode.lecteurs.size} lecteur(s) " +
                        "-> ${episode.lecteurs.joinToString { it.url }}"
                )
            }
        }
    }
}
