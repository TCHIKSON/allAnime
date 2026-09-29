package com.allanime.tv.data.scraper

import com.allanime.tv.core.DataException
import com.allanime.tv.domain.model.JourSemaine
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyse du planning hebdomadaire.
 *
 * Les fragments reproduisent la structure reelle des colonnes et des cartes, avec
 * les trois cas d'horaire que le site produit : une heure, un `?` et un report.
 */
class PlanningScraperTest {

    // Le client n'est jamais sollicite : seul `parse` est teste.
    private val scraper = PlanningScraper(
        client = OkHttpClient(),
        userAgent = "test",
        segmentCatalogue = "catalogue"
    )

    private fun carte(
        titre: String = "Liar Game",
        href: String = "/catalogue/liar-game/saison1/vostfr/",
        badge: String = BADGE_ANIME,
        langue: String = "VOSTFR",
        heure: String = "18h30",
        precision: String? = "Saison 1",
        reporte: Boolean = false,
        horodatage: String? = "1790008200"
    ): String {
        val attributHorodatage = horodatage?.let { """ data-release-ts="$it"""" }.orEmpty()
        val blocReporte = if (reporte) {
            """<div class="info-item release-time planning-problem-red">
                 <span class="info-text">Reporte</span>
               </div>"""
        } else {
            ""
        }
        val blocPrecision = precision?.let {
            """<div class="info-item episode">
                 <svg class="info-icon-svg"></svg>
                 <span class="info-text">$it</span>
               </div>"""
        }.orEmpty()

        return """
            <div class="anime-card-premium card-base planning-card"
                 data-title="${titre.lowercase()}"$attributHorodatage>
              <a href="$href">
                <div class="card-image-container">
                  <img class="card-image" src="https://cdn.tld/vignette.webp" alt="$titre">
                  $badge
                  <div class="language-badge-top badge">
                    <img class="flag-icon" src="https://cdn.tld/f.png" alt="$langue" title="$langue">
                  </div>
                </div>
                <div class="card-content">
                  <h2 class="card-title uppercase">$titre</h2>
                  <div class="card-info">
                    $blocReporte
                    <div class="info-item planning-time episode">
                      <svg class="time-icon-svg"></svg>
                      <span class="info-text font-bold text-base">$heure</span>
                    </div>
                    $blocPrecision
                  </div>
                </div>
              </a>
            </div>
        """
    }

    private fun colonne(jour: String, date: String, cartes: String) = """
        <div id="0" class="selectedRow px-1 py-4 rounded">
          <h2 class="titreJours">$jour                </h2>
          <p class="text-white text-center opacity-75">$date</p>
          <div class="categorieHome">$cartes</div>
        </div>
    """

    private fun page(colonnes: String, sansJourFixe: String = "") = """
        <html><body>
          <div id="planningClass" class="planningClass">$colonnes</div>
          <div class="scrollBarStyled grabScroll">$sansJourFixe</div>
        </body></html>
    """

    @Test
    fun `une carte donne tous les champs de la sortie`() {
        val planning = scraper.parse(page(colonne("Lundi", "21/09", carte())), horodatage = 42L)

        val jour = planning.jours.single()
        assertEquals(JourSemaine.LUNDI, jour.jour)
        assertEquals("21/09", jour.date)
        assertEquals(42L, planning.recupereLe)

        val sortie = jour.sorties.single()
        assertEquals("Liar Game", sortie.titre)
        assertEquals("liar-game", sortie.slug)
        assertEquals("Anime", sortie.type)
        assertEquals("VOSTFR", sortie.langue)
        assertEquals("18h30", sortie.heure)
        assertEquals("Saison 1", sortie.precision)
        assertEquals(1790008200L, sortie.horodatage)
        assertFalse(sortie.reporte)
        assertTrue(sortie.estAnime)
    }

    @Test
    fun `la precision de saison ne recupere pas l heure`() {
        // Le bloc d'heure porte lui aussi la classe `episode` : le selecteur doit
        // l'ecarter, faute de quoi la carte annonce « 18h30Saison 1 ».
        val sortie = scraper.parse(page(colonne("Lundi", "21/09", carte())))
            .jours.single().sorties.single()

        assertEquals("Saison 1", sortie.precision)
    }

    @Test
    fun `une heure inconnue vaut null et non un point d interrogation`() {
        val sortie = scraper.parse(page(colonne("Mardi", "22/09", carte(heure = "?"))))
            .jours.single().sorties.single()

        assertNull(sortie.heure)
    }

    @Test
    fun `un report est signale`() {
        val sortie = scraper.parse(
            page(colonne("Mercredi", "23/09", carte(reporte = true, heure = "20h00")))
        ).jours.single().sorties.single()

        assertTrue(sortie.reporte)
        assertEquals("20h00", sortie.heure)
    }

    @Test
    fun `un scan est reconnu et n est pas un anime`() {
        val sortie = scraper.parse(
            page(
                colonne(
                    "Lundi",
                    "21/09",
                    carte(
                        titre = "Ordeal",
                        href = "/catalogue/ordeal/scan/vf",
                        badge = BADGE_SCAN,
                        langue = "VF",
                        heure = "?",
                        precision = null,
                        horodatage = null
                    )
                )
            )
        ).jours.single().sorties.single()

        assertEquals("Scans", sortie.type)
        assertFalse(sortie.estAnime)
        assertEquals("ordeal", sortie.slug)
        assertNull(sortie.horodatage)
    }

    @Test
    fun `les sept jours sont reconnus a leur libelle`() {
        val colonnes = listOf(
            "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
        ).joinToString("") { colonne(it, "01/01", carte()) }

        val jours = scraper.parse(page(colonnes)).jours.map { it.jour }

        assertEquals(JourSemaine.entries.toList(), jours)
    }

    @Test
    fun `les oeuvres sans jour fixe sont recuperees`() {
        val hors = carte(
            titre = "Blue Lock",
            href = "/catalogue/blue-lock/scan/vf",
            heure = "?",
            precision = null,
            horodatage = null
        )

        val planning = scraper.parse(page(colonne("Lundi", "21/09", carte()), hors))

        assertEquals(listOf("blue-lock"), planning.sansJourFixe.map { it.slug })
        // La carte de la colonne ne doit pas s'y retrouver aussi.
        assertEquals(1, planning.jours.single().sorties.size)
    }

    @Test
    fun `le rappel d actualisation du site n est pas une sortie`() {
        val rappel = """
            <div class="planning-refresh-card">
              <span class="planning-refresh-title">Lundi soir</span>
            </div>
        """

        val planning = scraper.parse(page(colonne("Lundi", "21/09", carte() + rappel)))

        assertEquals(1, planning.jours.single().sorties.size)
    }

    @Test
    fun `une page sans colonne est signalee comme indisponible`() {
        val erreur = runCatching { scraper.parse("<html><body></body></html>") }.exceptionOrNull()

        assertTrue(erreur is DataException.CatalogueUnavailable)
    }

    @Test
    fun `le planning sait rendre les sorties d un jour donne`() {
        val colonnes = colonne("Lundi", "21/09", carte()) +
            colonne("Samedi", "26/09", carte(titre = "MAO", href = "/catalogue/mao/saison1/vostfr"))

        val planning = scraper.parse(page(colonnes))

        assertEquals(listOf("mao"), planning.sortiesDu(JourSemaine.SAMEDI).map { it.slug })
        assertTrue(planning.sortiesDu(JourSemaine.DIMANCHE).isEmpty())
        assertFalse(planning.estVide)
    }

    private companion object {
        const val BADGE_ANIME =
            """<div class="anime-badge badge"><span class="badge-text">Anime</span></div>"""
        const val BADGE_SCAN =
            """<div class="scan-badge badge"><span class="badge-text">Scans</span></div>"""
    }
}
