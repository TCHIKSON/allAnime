package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.Slugifier
import com.allanime.tv.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import java.net.URI

/**
 * Sonde : le site sert-il reellement une version differente par langue ?
 *
 * Le doute porte sur [SaisonsScraper] : il declare une langue disponible des que la
 * page repond 200. Si le site repond 200 pour n'importe quel segment de langue en
 * servant la version originale, l'application proposerait une VF qui n'existe pas —
 * et lirait de la VOSTFR en croyant lire de la VF.
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*SondeLanguesTest"
 */
@Ignore("Depend du reseau et de l'etat du catalogue ; a lancer manuellement.")
class SondeLanguesTest {

    private val episodesScraper = EpisodesScraper(HttpClients.shared)
    private val saisonsScraper = SaisonsScraper(HttpClients.shared, episodesScraper)

    @Test
    fun `comparer les episodes langue par langue`() = runBlocking {
        // Deux series reperees sur l'appareil : l'une annonce VOSTFR et VF.
        listOf("Darwin's Incident").forEach { titre ->
            val slug = Slugifier.slugify(titre)
            val catalogue = AppConfig.catalogueUrl(slug)
            println("\n===== $titre  ($catalogue)")

            val saisons = runCatching { saisonsScraper.fetch(catalogue) }.getOrNull()
            if (saisons.isNullOrEmpty()) {
                println("  aucune saison")
                return@forEach
            }

            saisons.forEach { saison ->
                println("  -- ${saison.nom} : ${saison.languesResume}")

                val empreintes = saison.langues.associate { langue ->
                    val url = URI(catalogue).resolve(langue.path).toString().trimEnd('/')
                    val episodes = runCatching { episodesScraper.fetch(url) }.getOrNull()

                    val premiere = episodes?.firstOrNull()?.lecteurs?.firstOrNull()?.url
                    println(
                        "     ${langue.code.code.padEnd(6)} " +
                            "${episodes?.size ?: 0} episodes | $url"
                    )
                    println("       1er lecteur : $premiere")

                    langue.code.code to (episodes?.map { it.lecteurs.map(::url) } ?: emptyList())
                }

                // Deux langues qui rendent exactement les memes URL ne sont qu'une
                // seule et meme version, servie sous deux adresses.
                val codes = empreintes.keys.toList()
                for (i in codes.indices) {
                    for (j in i + 1 until codes.size) {
                        val a = codes[i]
                        val b = codes[j]
                        if (empreintes[a] == empreintes[b] && empreintes[a]?.isNotEmpty() == true) {
                            println("     /!\\ $a et $b servent des lecteurs IDENTIQUES")
                        }
                    }
                }
            }
        }
    }

    private fun url(lecteur: com.allanime.tv.domain.model.Lecteur): String = lecteur.url
}
