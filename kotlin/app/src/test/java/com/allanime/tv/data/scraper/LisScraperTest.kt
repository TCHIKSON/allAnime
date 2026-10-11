package com.allanime.tv.data.scraper

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyse des pages du site de lecture, sur des extraits repris tels quels du site.
 *
 * Aucun appel reseau : seules les methodes `parse...` sont exercees, et la racine
 * est un nom d'hote d'exemple — rien ici ne depend du secret `BASE_URL_LIS`.
 */
class LisScraperTest {

    private val scraper = LisScraper(
        client = OkHttpClient(),
        baseUrl = RACINE,
        userAgent = "test"
    )

    @Test
    fun `la recherche rend le slug et le titre de chaque fiche`() {
        val html = """
            <a href="/clevatess-2/" class="va-search-result">
                <img src="https://images.exemple/clevatess.jpg" class="va-search-result-img">
                <div class="va-search-result-content">
                    <span class="va-search-result-title">Clevatess</span>
                    <span class="va-search-result-subtitle">Anime</span>
                </div>
            </a>
            <a href="$RACINE/one-piece/" class="va-search-result">
                <span class="va-search-result-title">One Piece</span>
            </a>
            <a href="/clevatess-2/saison-1/">Pas une fiche</a>
            <a href="https://ailleurs.exemple/clevatess/">Autre site</a>
        """.trimIndent()

        val resultats = scraper.parseRecherche(html)

        assertEquals(listOf("clevatess-2", "one-piece"), resultats.map { it.slug })
        assertEquals(listOf("Clevatess", "One Piece"), resultats.map { it.titre })
        assertEquals("https://images.exemple/clevatess.jpg", resultats.first().image)
        // « Anime » est un type, pas un titre alternatif.
        assertTrue(resultats.first().sousTitres.isEmpty())
    }

    @Test
    fun `l'adresse d'une fiche se termine par un slash`() {
        assertEquals("$RACINE/clevatess-2/", scraper.urlFiche("clevatess-2"))
    }

    @Test
    fun `les saisons sont lues sur leur carte et triees par numero`() {
        val html = """
            <a href="/clevatess-2/saison-1/" class="btn-watch">Regarder maintenant</a>
            ${carte("clevatess-2", "saison-2", "Saison 2", "13 épisodes", "VOSTFR", "VF")}
            ${carte("clevatess-2", "saison-1", "Saison 1", "12 épisodes", "VOSTFR")}
            <a href="/clevatess-2/saison-1/episode-1/vostfr/">Episode 1</a>
            <a href="/autre-anime/saison-1/">Voisin</a>
            <a href="$RACINE/clevatess-2/">Clevatess</a>
        """.trimIndent()

        val groupes = scraper.parseGroupes(html, "$RACINE/clevatess-2/")

        assertEquals(listOf("saison-1", "saison-2"), groupes.map { it.slug })
        // Le bouton « Regarder maintenant » pointe aussi sur la saison 1 : c'est la
        // carte qui donne le titre, pas le premier lien rencontre.
        assertEquals(listOf("Saison 1", "Saison 2"), groupes.map { it.titre })
        assertEquals(listOf(12, 13), groupes.map { it.nombreEpisodes })
        assertEquals(listOf("vostfr"), groupes[0].langues)
        assertEquals(listOf("vostfr", "vf"), groupes[1].langues)
        assertEquals("$RACINE/clevatess-2/saison-1/", groupes[0].url)
    }

    @Test
    fun `des sagas et des films gardent l'ordre de la fiche`() {
        val html = """
            ${carte("one-piece", "saga-2-alabasta-episode-62-a-143", "Saga 2 Alabasta", "82 épisodes", "VOSTFR")}
            ${carte("one-piece", "films", "Films", "19 épisodes", "VOSTFR")}
            ${carte("one-piece", "kai-saga-1-east-blue", "Kai - Saga 1", "20 épisodes", "VF")}
        """.trimIndent()

        val groupes = scraper.parseGroupes(html, "$RACINE/one-piece/")

        assertEquals(
            listOf("saga-2-alabasta-episode-62-a-143", "films", "kai-saga-1-east-blue"),
            groupes.map { it.slug }
        )
        assertEquals(listOf("saga", "section", "kai-saga"), groupes.map { it.type })
        assertEquals(listOf(2, null, 1), groupes.map { it.numero })
    }

    @Test
    fun `un groupe sans carte tire son titre de son adresse`() {
        val groupes = scraper.parseGroupes(
            """<a href="/clevatess-2/saison-1/">Regarder</a>""",
            "$RACINE/clevatess-2/"
        )

        assertEquals("Saison 1", groupes.single().titre)
        assertNull(groupes.single().nombreEpisodes)
        assertTrue(groupes.single().langues.isEmpty())
    }

    @Test
    fun `les episodes sont tries et portent toutes leurs langues`() {
        val html = """
            <a href="/clevatess-2/saison-1/episode-10/vostfr/" class="episode-card" data-langs="vostfr vf">10</a>
            <a href="/clevatess-2/saison-1/episode-2/vostfr/" class="episode-card" data-langs="vostfr">2</a>
            <a href="/clevatess-2/saison-1/episode-1/vostfr/" class="episode-card">1</a>
            <a href="/clevatess-2/saison-1/episode-1/vf/">1 en VF</a>
            <a href="/clevatess-2/saison-2/episode-1/vostfr/">Autre saison</a>
            <a href="/clevatess-2/saison-1/">La saison elle-meme</a>
        """.trimIndent()

        val episodes = scraper.parseEpisodes(html, "$RACINE/clevatess-2/saison-1/")

        assertEquals(listOf(1, 2, 10), episodes.map { it.numero })
        assertEquals(listOf("vostfr", "vf"), episodes[0].langues)
        assertEquals(listOf("vostfr"), episodes[1].langues)
        assertEquals(listOf("vostfr", "vf"), episodes[2].langues)
        assertEquals("$RACINE/clevatess-2/saison-1/episode-1/vostfr/", episodes[0].url)
    }

    @Test
    fun `les lecteurs sont lus langue par langue`() {
        val html = """
            <script>
            const readers = {"vostfr":["https:\/\/ansembed.net\/embed-kz6tinna9qm8.html","https:\/\/lpayer.embed4me.com\/#pbuhz"],"vf":["https:\/\/movearnpre.com\/embed\/febbqfy40njx"],"va":[]};
            const animeId = 1699;
            </script>
        """.trimIndent()

        val lecteurs = scraper.parseLecteurs(html)

        assertEquals(setOf("vostfr", "vf"), lecteurs.keys)
        assertEquals(
            listOf("https://ansembed.net/embed-kz6tinna9qm8.html", "https://lpayer.embed4me.com/#pbuhz"),
            lecteurs["vostfr"]
        )
        assertEquals(listOf("https://movearnpre.com/embed/febbqfy40njx"), lecteurs["vf"])
    }

    @Test
    fun `sans declaration les lecteurs en iframe servent de repli`() {
        val html = """
            <iframe src="https://ansembed.net/embed-abc123.html"></iframe>
            <iframe data-src="https://movearnpre.com/embed/def456"></iframe>
            <iframe src="about:blank"></iframe>
        """.trimIndent()

        assertEquals(
            mapOf(
                LisScraper.LANGUE_PAR_DEFAUT to listOf(
                    "https://ansembed.net/embed-abc123.html",
                    "https://movearnpre.com/embed/def456"
                )
            ),
            scraper.parseLecteurs(html)
        )
    }

    @Test
    fun `une page sans lecteur ne rend rien`() {
        assertTrue(scraper.parseLecteurs("<p>Bientot disponible</p>").isEmpty())
        assertTrue(scraper.parseLecteurs("const readers = {pas du json};").isEmpty())
    }

    @Test
    fun `un film porte une adresse par langue`() {
        val html = """
            const filmUrls = {"vostfr":"https:\/\/ansembed.net\/embed-film1.html","vf":""};
        """.trimIndent()

        assertEquals(
            mapOf("vostfr" to listOf("https://ansembed.net/embed-film1.html")),
            scraper.parseFilm(html)
        )
    }

    /** Une carte de saison, telle que la fiche du site la publie. */
    private fun carte(
        fiche: String,
        slug: String,
        titre: String,
        compte: String,
        vararg langues: String
    ) = """
        <a href="/$fiche/$slug/" class="season-card" data-season="$slug">
            <div class="season-card-header">
                <div class="season-eyebrow">Clevatess</div>
                <h3 class="season-title">$titre</h3>
                <div class="season-meta">
                    <span>$compte</span>
                    <div class="season-languages">
                        ${langues.joinToString("") { """<span class="lang-badge">$it</span>""" }}
                    </div>
                </div>
            </div>
            <div class="season-card-footer"><span class="season-link">Regarder</span></div>
        </a>
    """

    private companion object {
        const val RACINE = "https://lecture.exemple"
    }
}
