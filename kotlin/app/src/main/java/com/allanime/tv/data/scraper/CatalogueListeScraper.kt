package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.LienCatalogue
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.PageCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Port Kotlin de `lecteur-videos/parserCatalogue.js`.
 *
 * Le script de reference charge la page `/catalogue` filtree sur le type « Anime »
 * et transforme chaque carte `.catalog-card` en objet JSON. On lit exactement les
 * memes selecteurs ici, Jsoup remplacant Cheerio.
 *
 * Trois ecarts assumes par rapport au script Node, signales sur place :
 *  - la pagination est lue une fois pour la page entiere, la ou le script la recopie
 *    dans chaque carte ;
 *  - le lien de la carte est reduit a son slug, parce que le site sert parfois des
 *    liens absolus vers un autre nom de domaine que celui configure ;
 *  - les etiquettes de genre coupees par le site sont recollees (voir [genres]).
 */
class CatalogueListeScraper(
    private val client: OkHttpClient,
    private val userAgent: String = AppConfig.scraperUserAgent,
    private val segmentCatalogue: String = AppConfig.segmentCatalogue
) {

    /** Telecharge et analyse une page de la liste. */
    suspend fun fetch(url: String): PageCatalogue {
        val html = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw DataException.CatalogueUnavailable(
                        "Liste du catalogue indisponible (${response.code}) : $url"
                    )
                }
                response.body?.string().orEmpty()
            }
        }

        return parse(html)
    }

    /**
     * Transforme le HTML d'une page de liste en modeles.
     *
     * Visible pour les tests : c'est la partie sensible du port.
     */
    fun parse(html: String): PageCatalogue {
        val document = Jsoup.parse(html)

        val animes = document.select(CARTE)
            .mapNotNull { carte -> versAnime(carte) }
            // Le site republie parfois la meme fiche sur deux pages : le slug tranche.
            .distinctBy { it.slug }

        return PageCatalogue(animes = animes, nombrePages = nombrePages(document))
    }

    private fun versAnime(carte: Element): AnimeCatalogue? {
        val slug = LienCatalogue.slug(carte.selectFirst("a")?.attr("href"), segmentCatalogue)
            ?: return null
        val titre = carte.selectFirst(TITRE)?.text()?.trim().orEmpty()
        if (titre.isEmpty()) return null

        return AnimeCatalogue(
            slug = slug,
            titre = titre,
            titresAlternatifs = titresAlternatifs(carte),
            image = carte.selectFirst(IMAGE)?.attr("src")?.trim()?.takeIf { it.isNotEmpty() },
            genres = genres(carte),
            types = valeursSeparees(carte.selectFirst(TYPES)?.text()),
            langues = carte.select(LANGUE).mapNotNull { drapeau ->
                drapeau.attr("title").trim().takeIf { it.isNotEmpty() }
            },
            synopsis = carte.selectFirst(SYNOPSIS)?.text()?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun titresAlternatifs(carte: Element): List<String> =
        valeursSeparees(carte.selectFirst(TITRES_ALTERNATIFS)?.text())

    /**
     * Genres de la carte, etiquette par etiquette.
     *
     * Deux nettoyages sont necessaires :
     *  - la derniere etiquette vaut parfois `…`, qui signale seulement que la carte
     *    manque de place ; ce n'est pas un genre ;
     *  - le site coupe `Science-fiction` et `Science-Fantasy` en deux etiquettes.
     *    Laissees telles quelles, elles produiraient sur l'accueil deux rangees
     *    « Science » et « fiction » qui ne veulent rien dire : une etiquette valant
     *    exactement `Science` est donc recollee a celle qui la suit.
     */
    fun genres(carte: Element): List<String> {
        val etiquettes = carte.select(GENRE)
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && it != ELLIPSE }

        val recollees = mutableListOf<String>()
        var position = 0
        while (position < etiquettes.size) {
            val etiquette = etiquettes[position]
            val suivante = etiquettes.getOrNull(position + 1)

            if (etiquette.equals(PREFIXE_COUPE, ignoreCase = true) && suivante != null) {
                recollees += "$etiquette-$suivante"
                position += 2
            } else {
                recollees += etiquette
                position += 1
            }
        }

        return recollees.distinct()
    }

    /**
     * Nombre de pages annonce par le bloc de pagination.
     *
     * Une liste qui tient sur une seule page n'affiche aucun lien : on retombe alors
     * sur 1 plutot que sur 0, ce qui ferait croire a une page absente.
     */
    private fun nombrePages(document: org.jsoup.nodes.Document): Int =
        document.select(PAGINATION)
            .mapNotNull { lien -> lien.text().trim().toIntOrNull() }
            .maxOrNull()
            ?: 1

    /** `Anime, Film` devient `[Anime, Film]` ; une chaine vide devient une liste vide. */
    private fun valeursSeparees(brut: String?): List<String> =
        brut?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private companion object {
        const val CARTE = ".catalog-card"
        const val TITRE = ".card-title"
        const val TITRES_ALTERNATIFS = ".alternate-titles"
        const val IMAGE = ".card-image"
        const val GENRE = ".genre-tags .genre-tag"
        const val TYPES = ".type-row .info-value"
        const val LANGUE = ".lang-flags .lang-flag"
        const val SYNOPSIS = ".synopsis-content"
        const val PAGINATION = "#list_pagination a"

        /** Etiquette de debordement ajoutee par le site quand la carte est pleine. */
        const val ELLIPSE = "…"

        /** Seul mot que le site detache de son genre composé. */
        const val PREFIXE_COUPE = "Science"
    }
}
