package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.jsoup.Jsoup
import okhttp3.Request

/**
 * Sonde : combien de saisons la page **declare**, combien en restent-ils ?
 *
 * Le catalogue est bati sur un gabarit que les editeurs recopient : beaucoup de
 * series y declarent « Saison 1 » a « Saison 10 », « Film » et « OAV » alors qu'une
 * seule existe. Les panneaux ne sont pas en commentaire, ils sont bel et bien
 * appeles — impossible de trancher a la lecture de la page.
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*SondePanneauxTest"
 */
@Ignore("Depend du reseau et de l'etat du catalogue ; a lancer manuellement.")
class SondePanneauxTest {

    private val motif = Regex("""panneauAnime\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)""")
    private val episodesScraper = EpisodesScraper(HttpClients.shared)
    private val saisonsScraper = SaisonsScraper(HttpClients.shared, episodesScraper)

    @Test
    fun `declare contre retenu`() = runBlocking {
        listOf("darwins-incident", "clevatess", "classroom-of-the-elite").forEach { slug ->
            val url = AppConfig.catalogueUrl(slug)
            println("\n===== $slug")

            val brut = Jsoup.parse(telecharger(url))
                .select("div.flex.flex-wrap.overflow-y-hidden")
                .select("script")
                .joinToString("\n") { it.data() }

            val declares = motif.findAll(brut)
                .map { it.groupValues[1] }
                .filterNot { it.equals("nom", ignoreCase = true) }
                .toList()

            println("  DECLARE (${declares.size}) : ${declares.joinToString(", ")}")

            val retenues = saisonsScraper.fetch(url)
            println("  RETENU  (${retenues.size}) :")
            retenues.forEach { saison ->
                println(
                    "     ${saison.nom.padEnd(12)} ${saison.nombreEpisodes ?: 0} episodes " +
                        "| ${saison.languesResume}"
                )
            }
        }
    }

    private fun telecharger(url: String): String {
        val requete = Request.Builder()
            .url(url)
            .header("User-Agent", AppConfig.scraperUserAgent)
            .get()
            .build()
        return HttpClients.shared.newCall(requete).execute().use { it.body?.string().orEmpty() }
    }
}
