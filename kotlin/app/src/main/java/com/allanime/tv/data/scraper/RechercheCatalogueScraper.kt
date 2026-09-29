package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.LienCatalogue
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.ResultatRecherche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Port Kotlin de `lecteur-videos/parserSearch.js`.
 *
 * Le moteur de recherche du site repond a une requete `POST` en
 * `application/x-www-form-urlencoded` portant un seul champ, `query`. Sa reponse est
 * un fragment HTML : une suite de liens `.asn-search-result`, chacun portant le lien
 * de la fiche, sa vignette, son titre et ses titres alternatifs.
 *
 * Les selecteurs sont repris tels quels du script de reference. Deux points
 * different, et chacun corrige une approximation :
 *
 *  - le slug n'est pas le dernier segment brut de l'URL mais celui qui suit le
 *    segment du catalogue ([LienCatalogue]), le site servant parfois des liens
 *    absolus vers un autre hote ;
 *  - les titres alternatifs, rendus par le script comme une seule chaine, sont
 *    decoupes sur les virgules : c'est entree par entree qu'ils sont compares.
 */
class RechercheCatalogueScraper(
    private val client: OkHttpClient,
    private val userAgent: String = AppConfig.scraperUserAgent,
    private val segmentCatalogue: String = AppConfig.segmentCatalogue
) {

    /**
     * Interroge le moteur de recherche du site.
     *
     * @param titre valeur du champ `query`.
     * @return les entrees trouvees, dans l'ordre du site — c'est son classement.
     */
    suspend fun rechercher(titre: String, url: String = AppConfig.rechercheUrl): List<ResultatRecherche> {
        val html = withContext(Dispatchers.IO) {
            val corps = FormBody.Builder().add("query", titre).build()
            val requete = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .post(corps)
                .build()

            client.newCall(requete).await().use { reponse ->
                if (!reponse.isSuccessful) {
                    throw DataException.CatalogueUnavailable(
                        "Recherche du catalogue indisponible (${reponse.code}) : $url"
                    )
                }
                reponse.body?.string().orEmpty()
            }
        }

        return parse(html)
    }

    /**
     * Transforme le fragment HTML en entrees.
     *
     * Visible pour les tests : c'est la partie sensible du port.
     */
    fun parse(html: String): List<ResultatRecherche> = Jsoup.parse(html)
        .select(".asn-search-result")
        .mapNotNull { element ->
            val slug = LienCatalogue.slug(element.attr("href"), segmentCatalogue)
                ?: return@mapNotNull null
            val titre = element.selectFirst(".asn-search-result-title")?.text()?.trim()
            if (titre.isNullOrBlank()) return@mapNotNull null

            ResultatRecherche(
                slug = slug,
                titre = titre,
                sousTitres = element.selectFirst(".asn-search-result-subtitle")
                    ?.text()
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty(),
                image = element.selectFirst(".asn-search-result-img")
                    ?.attr("src")
                    ?.takeIf { it.isNotBlank() }
            )
        }
        .distinctBy { it.slug }
}
