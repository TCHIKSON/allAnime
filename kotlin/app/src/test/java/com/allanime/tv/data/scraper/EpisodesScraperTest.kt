package com.allanime.tv.data.scraper

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie le port de `scripts/episode.js` sur un `episodes.js` reellement servi par
 * le catalogue (saison 1 VOSTFR de « classroom-of-the-elite »).
 */
class EpisodesScraperTest {

    private val scraper = EpisodesScraper(OkHttpClient(), "test")

    private val script: String = requireNotNull(
        javaClass.getResourceAsStream("/episodes-saison1-vostfr.js")
    ).bufferedReader().use { it.readText() }

    @Test
    fun `le nombre d episodes est celui du tableau le plus long`() {
        val episodes = scraper.parse(script)

        assertEquals(12, episodes.size)
        assertEquals(1, episodes.first().numero)
        assertEquals(12, episodes.last().numero)
    }

    @Test
    fun `chaque episode expose un lecteur par variable eps numerotee`() {
        val episodes = scraper.parse(script)

        // eps1, eps2 et eps3 : trois lecteurs par episode.
        episodes.forEach { episode ->
            assertEquals(3, episode.lecteurs.size)
            assertTrue(episode.estDisponible)
        }
    }

    @Test
    fun `les lecteurs sont numerotes dans l ordre d affichage`() {
        val premier = scraper.parse(script).first()

        assertEquals(listOf(1, 2, 3), premier.lecteurs.map { it.index })
    }

    @Test
    fun `le tableau epsAS des fichiers directs est ignore`() {
        // Ces URL visent le domaine historique anime-sama.fr, qui ne resout plus :
        // les exposer donnerait un lecteur systematiquement en echec.
        val lecteurs = scraper.parse(script).flatMap { it.lecteurs }

        assertTrue(lecteurs.none { it.url.contains("anime-sama.fr") })
        assertTrue(lecteurs.none { it.url.endsWith(".mp4") })
    }

    @Test
    fun `les lecteurs suivent l ordre des variables numerotees`() {
        val premier = scraper.parse(script).first()

        assertEquals(
            listOf("sibnet", "ansembed", "sendvid"),
            premier.lecteurs.map { it.hostLabel }
        )
    }

    @Test
    fun `la case i de chaque tableau correspond a l episode i plus un`() {
        val episodes = scraper.parse(script)

        assertEquals(
            "https://video.sibnet.ru/shell.php?videoid=4668080",
            episodes[0].lecteurs[0].url
        )
        assertEquals(
            "https://video.sibnet.ru/shell.php?videoid=4668082",
            episodes[1].lecteurs[0].url
        )
    }

    @Test
    fun `un script sans tableau eps ne produit aucun episode`() {
        assertTrue(scraper.parse("var autreChose = 12;").isEmpty())
        assertTrue(scraper.parse("").isEmpty())
    }

    @Test
    fun `un tableau plus court ne decale pas les autres lecteurs`() {
        val partiel = """
            var eps1 = ['https://a.tld/1', 'https://a.tld/2', 'https://a.tld/3'];
            var eps2 = ['https://b.tld/1'];
        """.trimIndent()

        val episodes = scraper.parse(partiel)

        assertEquals(3, episodes.size)
        assertEquals(2, episodes[0].lecteurs.size)
        assertEquals(1, episodes[1].lecteurs.size)
        assertEquals("https://a.tld/2", episodes[1].lecteurs.first().url)
    }
}
