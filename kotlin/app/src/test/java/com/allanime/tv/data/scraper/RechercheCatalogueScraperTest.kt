package com.allanime.tv.data.scraper

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analyse de la reponse du moteur de recherche du site.
 *
 * L'echantillon est celui du script de reference `parserSearch.js`, recopie tel
 * quel dans les ressources de test : entites HTML, sous-titre vide et liens absolus
 * compris.
 */
class RechercheCatalogueScraperTest {

    // Le client n'est jamais sollicite : seul `parse` est teste.
    private val scraper = RechercheCatalogueScraper(
        client = OkHttpClient(),
        userAgent = "test",
        segmentCatalogue = "catalogue"
    )

    private val echantillon: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("recherche-catalogue.html"))
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `les cinq resultats de l echantillon sont lus`() {
        val resultats = scraper.parse(echantillon)

        assertEquals(5, resultats.size)
        assertEquals(
            listOf("no-6", "clevatess", "oshi-no-ko", "to-be-hero", "chi-no-yume"),
            resultats.map { it.slug }
        )
    }

    @Test
    fun `titre vignette et titres alternatifs sont repris`() {
        val clevatess = scraper.parse(echantillon).first { it.slug == "clevatess" }

        assertEquals("Clevatess", clevatess.titre)
        assertTrue(clevatess.image.orEmpty().endsWith("clevatess.webp"))
        assertEquals(
            "Clevatess: Majuu no Ou to Akago to Shikabane no Yuusha",
            clevatess.sousTitres.first()
        )

        // Le site separe les titres alternatifs par des virgules, mais certains en
        // contiennent : les sept morceaux ne sont pas sept titres. Le sous-titre
        // recompose figure donc aussi parmi les formes comparables, entier.
        assertEquals(7, clevatess.sousTitres.size)
        assertTrue(
            clevatess.formes.any {
                it.contains("the-king-of-devil-beasts-the-baby-and-the-brave-of-undead")
            }
        )
    }

    @Test
    fun `les entites HTML sont decodees`() {
        val oshiNoKo = scraper.parse(echantillon).first { it.slug == "oshi-no-ko" }

        // Le site sert `&quot;` et `&#039;` ; ils ne doivent pas rester tels quels.
        assertEquals(
            listOf("\"Oshi no Ko\"", "My Favorite Idol's Children"),
            oshiNoKo.sousTitres
        )
    }

    @Test
    fun `un sous-titre vide ne produit pas d entree vide`() {
        val chiNoYume = scraper.parse(echantillon).first { it.slug == "chi-no-yume" }

        assertEquals(emptyList<String>(), chiNoYume.sousTitres)
    }

    @Test
    fun `une reponse vide ne donne aucun resultat`() {
        assertEquals(emptyList<Any>(), scraper.parse(""))
        assertEquals(emptyList<Any>(), scraper.parse("<div>rien a signaler</div>"))
    }

    @Test
    fun `un resultat sans titre est ignore plutot que devine`() {
        val fragment = """
            <a href="https://exemple.tld/catalogue/sans-titre" class="asn-search-result">
                <div class="asn-search-result-content"></div>
            </a>
        """.trimIndent()

        assertEquals(emptyList<Any>(), scraper.parse(fragment))
    }
}
