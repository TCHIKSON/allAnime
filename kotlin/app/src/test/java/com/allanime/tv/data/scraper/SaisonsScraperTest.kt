package com.allanime.tv.data.scraper

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie la lecture des panneaux de saison sur un extrait reel de fiche catalogue.
 */
class SaisonsScraperTest {

    private val scraper = SaisonsScraper(OkHttpClient(), EpisodesScraper(OkHttpClient(), "test"), "test")

    private val html: String = requireNotNull(
        javaClass.getResourceAsStream("/catalogue-fiche.html")
    ).bufferedReader().use { it.readText() }

    @Test
    fun `les quatre saisons sont retrouvees`() {
        val panneaux = scraper.parsePanneaux(html)

        assertEquals(
            listOf("Saison 1", "Saison 2", "Saison 3", "Saison 4"),
            panneaux.map { it.nom }
        )
        assertEquals(
            listOf("saison1/vostfr", "saison2/vostfr", "saison3/vostfr", "saison4/vostfr"),
            panneaux.map { it.path }
        )
    }

    @Test
    fun `le gabarit en commentaire est ignore`() {
        // La page contient `panneauAnime("nom", "url")` en commentaire : le script
        // Node le laisse passer et cree une saison fantome, pas le port Kotlin.
        val panneaux = scraper.parsePanneaux(html)

        assertTrue(panneaux.none { it.nom == "nom" || it.path == "url" })
    }

    @Test
    fun `la definition de la fonction n est pas confondue avec un appel`() {
        val panneaux = scraper.parsePanneaux(html)

        assertTrue(panneaux.none { it.path == "url" })
        assertEquals(4, panneaux.size)
    }

    @Test
    fun `les panneaux de scans ne sont pas pris pour des saisons`() {
        val panneaux = scraper.parsePanneaux(html)

        assertTrue(panneaux.none { it.path.startsWith("scan") })
    }

    @Test
    fun `la racine d une saison retire le segment de langue`() {
        val panneaux = scraper.parsePanneaux(html)

        assertEquals("saison1", panneaux.first().racine)
    }

    @Test
    fun `un chemin sans segment de langue reste inchange`() {
        val panneau = SaisonsScraper.PanneauSaison(nom = "Film", path = "film")

        assertEquals("film", panneau.racine)
    }

    @Test
    fun `une page sans panneau ne renvoie rien`() {
        assertTrue(scraper.parsePanneaux("<html><body><p>rien</p></body></html>").isEmpty())
    }
}
