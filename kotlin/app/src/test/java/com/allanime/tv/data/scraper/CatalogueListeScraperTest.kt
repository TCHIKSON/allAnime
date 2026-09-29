package com.allanime.tv.data.scraper

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyse de la liste du catalogue.
 *
 * Les fragments reproduisent la structure reelle des cartes, y compris ses defauts :
 * etiquette de debordement, genre coupe en deux et retours chariot dans le texte.
 */
class CatalogueListeScraperTest {

    // Le client n'est jamais sollicite : seul `parse` est teste.
    private val scraper = CatalogueListeScraper(
        client = OkHttpClient(),
        userAgent = "test",
        segmentCatalogue = "catalogue"
    )

    private fun carte(
        href: String = "https://exemple.tld/catalogue/a-silent-voice/",
        titre: String = "A Silent Voice",
        alternatifs: String = "The Shape of Voice, Koe Katachi",
        genres: String = """
            <span class="genre-tag">Drame</span>
            <span class="genre-tag">Romance</span>
        """,
        types: String = "Anime, Film",
        langues: String = """
            <span class="lang-flag" title="JP"><svg></svg></span>
            <span class="lang-flag" title="FR"><svg></svg></span>
        """,
        synopsis: String = "Un synopsis."
    ) = """
        <div class="shrink-0 catalog-card card-base">
          <a href="$href">
            <div class="card-image-container">
              <img class="card-image" src="https://cdn.tld/thumb.webp" alt="$titre">
            </div>
            <div class="card-content">
              <h2 class="card-title">$titre</h2>
              <p class="alternate-titles">$alternatifs</p>
              <div class="catalog-info">
                <div class="info-row">
                  <div class="genre-tags">$genres</div>
                </div>
                <div class="info-row">
                  <div class="type-row"><p class="info-value">$types</p></div>
                </div>
                <div class="info-row">
                  <div class="lang-flags">$langues</div>
                </div>
              </div>
              <div class="synopsis-tooltip">
                <div class="synopsis-content">$synopsis</div>
              </div>
            </div>
          </a>
        </div>
    """

    private fun page(cartes: String, pagination: String = "") =
        """<html><body><div id="list_catalog">$cartes</div>$pagination</body></html>"""

    @Test
    fun `une carte donne tous les champs de la fiche`() {
        val anime = scraper.parse(page(carte())).animes.single()

        assertEquals("a-silent-voice", anime.slug)
        assertEquals("A Silent Voice", anime.titre)
        assertEquals(listOf("The Shape of Voice", "Koe Katachi"), anime.titresAlternatifs)
        assertEquals("https://cdn.tld/thumb.webp", anime.image)
        assertEquals(listOf("Drame", "Romance"), anime.genres)
        assertEquals(listOf("Anime", "Film"), anime.types)
        assertEquals(listOf("JP", "FR"), anime.langues)
        assertEquals("Un synopsis.", anime.synopsis)
    }

    @Test
    fun `le slug est lu dans le chemin, pas dans le nom d hote`() {
        // Le site change de domaine sans prevenir : seul le chemin fait foi.
        val page = page(carte(href = "https://un-autre-domaine.to/catalogue/akira"))

        assertEquals("akira", scraper.parse(page).animes.single().slug)
    }

    @Test
    fun `l etiquette de debordement n est pas un genre`() {
        val page = page(
            carte(
                genres = """
                    <span class="genre-tag">Action</span>
                    <span class="genre-tag">Combats</span>
                    <span class="genre-tag" style="opacity: 0.5;">…</span>
                """
            )
        )

        assertEquals(listOf("Action", "Combats"), scraper.parse(page).animes.single().genres)
    }

    @Test
    fun `un genre coupe par le site est recolle`() {
        // Le site publie « Science-fiction » en deux etiquettes.
        val page = page(
            carte(
                genres = """
                    <span class="genre-tag">Science</span>
                    <span class="genre-tag">fiction</span>
                    <span class="genre-tag">Cyberpunk</span>
                """
            )
        )

        assertEquals(
            listOf("Science-fiction", "Cyberpunk"),
            scraper.parse(page).animes.single().genres
        )
    }

    @Test
    fun `les retours chariot du site sont retires des genres`() {
        val page = page(
            carte(genres = "<span class=\"genre-tag\">Fantasy\r\n</span>")
        )

        assertEquals(listOf("Fantasy"), scraper.parse(page).animes.single().genres)
    }

    @Test
    fun `une carte sans titre alternatif ne produit pas de titre vide`() {
        val anime = scraper.parse(page(carte(alternatifs = ""))).animes.single()

        assertTrue(anime.titresAlternatifs.isEmpty())
        assertEquals(listOf("A Silent Voice"), anime.titresPourRecherche)
    }

    @Test
    fun `une carte sans lien exploitable est ignoree`() {
        assertTrue(scraper.parse(page(carte(href = ""))).animes.isEmpty())
    }

    @Test
    fun `la meme fiche listee deux fois n apparait qu une fois`() {
        val page = page(carte() + carte())

        assertEquals(1, scraper.parse(page).animes.size)
    }

    @Test
    fun `le nombre de pages vient du bloc de pagination`() {
        val pagination = """
            <div id="list_pagination">
              <a href='?page=1'>1</a>
              <a href='?page=2'>2</a>
              <a href='?page=3'>3</a>
            </div>
        """

        assertEquals(3, scraper.parse(page(carte(), pagination)).nombrePages)
    }

    @Test
    fun `sans pagination la liste tient sur une page`() {
        assertEquals(1, scraper.parse(page(carte())).nombrePages)
    }

    @Test
    fun `une page sans carte ne produit rien plutot que de planter`() {
        val page = scraper.parse("<html><body></body></html>")

        assertTrue(page.animes.isEmpty())
        assertEquals(1, page.nombrePages)
    }

    @Test
    fun `un film est reconnu comme tel`() {
        val film = scraper.parse(page(carte(types = "Anime, Film"))).animes.single()
        val serie = scraper.parse(page(carte(types = "Anime"))).animes.single()

        assertTrue(film.estFilm)
        assertTrue(!serie.estFilm)
    }

    @Test
    fun `un synopsis absent vaut null et non une chaine vide`() {
        assertNull(scraper.parse(page(carte(synopsis = ""))).animes.single().synopsis)
    }
}
