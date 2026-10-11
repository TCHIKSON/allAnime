package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.ResultatRecherche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Port Kotlin de `scripts/lis.js`, le script de reference qui prend la racine du site
 * (`BASE_URL_LIS`) et le titre d'un anime, et rend ses saisons, ses episodes et leurs
 * lecteurs.
 *
 * Le script enchaine quatre lectures, reprises ici une par une :
 *
 *  1. [rechercher] — `searchJson` : le moteur de recherche du site donne la fiche ;
 *  2. [groupes] — `getSeasonLinks` : la fiche liste ses saisons, sagas, films... ;
 *  3. [episodes] — `getEpisodeLinks` : la page d'un groupe liste ses episodes ;
 *  4. [lecteurs] — `getEpisodeMetadata` : la page d'un episode declare ses lecteurs,
 *     langue par langue, dans `const readers = {...}`.
 *
 * **Un ecart assume : rien n'est parcouru d'avance.** Le script visite chaque episode
 * pour produire son JSON d'un bloc ; sur un televiseur, cela ferait deux cents
 * requetes pour ouvrir une saga de One Piece. Les memes donnees sont donc lues a la
 * demande : la fiche a l'ouverture de la serie, la page d'un groupe quand on le
 * selectionne, les lecteurs d'un episode quand on le lance. Les cartes du site
 * portent deja le nombre d'episodes et les langues, ce qui rend ce decoupage gratuit.
 *
 * Les methodes `parse...` sont visibles pour les tests : c'est la partie sensible.
 */
class LisScraper(
    private val client: OkHttpClient,
    private val baseUrl: String = AppConfig.lisBaseUrl,
    private val userAgent: String = AppConfig.scraperUserAgent
) {

    /** Un groupe d'episodes de la fiche : saison, saga, films... */
    data class Groupe(
        /** Dernier segment de son adresse : `saison-1`, `saga-2-alabasta-...`. */
        val slug: String,
        val titre: String,
        val url: String,
        /** Annonce par la carte de la fiche ; `null` si elle ne le dit pas. */
        val nombreEpisodes: Int?,
        /** Segments de langue annonces par la carte : `vostfr`, `vf`... */
        val langues: List<String>
    ) {
        /** `saison`, `saga`, `kai-saga`, ou `section` pour tout le reste. */
        val type: String
            get() = when {
                slug.startsWith("saison-") -> "saison"
                slug.startsWith("kai-saga-") -> "kai-saga"
                slug.startsWith("saga-") -> "saga"
                else -> "section"
            }

        val numero: Int?
            get() = NUMERO_GROUPE.find(slug)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Un episode tel que la page de son groupe le liste. */
    data class EpisodeListe(
        val numero: Int,
        val url: String,
        val langues: List<String>
    )

    /**
     * Interroge le moteur de recherche du site.
     *
     * @return les fiches trouvees, dans l'ordre du site. [ResultatRecherche.slug]
     *   est le chemin de la fiche, a resoudre par [urlFiche].
     */
    suspend fun rechercher(titre: String): List<ResultatRecherche> {
        val url = racine().resolve(CHEMIN_RECHERCHE)
            ?: throw DataException.CatalogueUnavailable("Adresse de recherche invalide")

        val html = withContext(Dispatchers.IO) {
            val requete = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .post(FormBody.Builder().add("query", titre).build())
                .build()

            client.newCall(requete).await().use { reponse ->
                if (!reponse.isSuccessful) {
                    throw DataException.CatalogueUnavailable(
                        "Recherche indisponible (${reponse.code}) : $url"
                    )
                }
                reponse.body?.string().orEmpty()
            }
        }

        return parseRecherche(html)
    }

    /** Adresse de la fiche d'un slug rendu par [rechercher]. */
    fun urlFiche(slug: String): String =
        racine().newBuilder().addPathSegment(slug).addPathSegment("").build().toString()

    /** Groupes de la fiche, dans l'ordre retenu par le script. */
    suspend fun groupes(urlFiche: String): List<Groupe> =
        parseGroupes(telecharger(urlFiche), urlFiche)

    /** Episodes d'un groupe, du premier au dernier. */
    suspend fun episodes(urlGroupe: String): List<EpisodeListe> =
        parseEpisodes(telecharger(urlGroupe), urlGroupe)

    /** Lecteurs d'un episode, par segment de langue. */
    suspend fun lecteurs(urlEpisode: String): Map<String, List<String>> =
        parseLecteurs(telecharger(urlEpisode))

    /**
     * Lecteurs d'un film publie sans groupe, par segment de langue.
     *
     * C'est la branche `getMovieMetadata` du script : la fiche porte alors elle-meme
     * `const filmUrls = {...}`, ou a defaut un lecteur en `<iframe>`.
     */
    suspend fun film(urlFiche: String): Map<String, List<String>> =
        parseFilm(telecharger(urlFiche))

    fun parseRecherche(html: String): List<ResultatRecherche> = Jsoup.parse(html)
        .select("a[href]")
        .mapNotNull { lien ->
            // Seule une fiche a un chemin d'un seul segment : `/clevatess-2/`.
            val slug = racine().resolve(lien.attr("href"))
                ?.takeIf { it.host == racine().host }
                ?.segments()
                ?.singleOrNull()
                ?: return@mapNotNull null

            val titre = lien.selectFirst(".va-search-result-title")?.text()
                ?.takeIf { it.isNotBlank() }
                ?: lien.text()
            if (titre.isBlank()) return@mapNotNull null

            ResultatRecherche(
                slug = slug,
                titre = titre.trim(),
                // Le sous-titre du site est ici le type (« Anime »), pas un titre
                // alternatif : le comparer a un titre ne ferait que du bruit.
                sousTitres = emptyList(),
                image = lien.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
            )
        }
        .distinctBy { it.slug }

    fun parseGroupes(html: String, urlFiche: String): List<Groupe> {
        val page = urlFiche.toHttpUrlOrNull() ?: return emptyList()
        val slugFiche = page.segments().firstOrNull() ?: return emptyList()

        val groupes = Jsoup.parse(html)
            .select("a[href]")
            .mapNotNull { lien ->
                val cible = page.resolve(lien.attr("href")) ?: return@mapNotNull null
                val segments = cible.segments()
                val direct = cible.host == page.host &&
                    segments.size == 2 &&
                    segments[0] == slugFiche &&
                    !SEGMENT_EPISODE.matches(segments[1])
                if (direct) segments[1] to lien else null
            }
            .groupBy({ it.first }, { it.second })
            .map { (slug, liens) ->
                // Un meme groupe est lie plusieurs fois (bouton « Regarder », carte) :
                // seule la carte porte le titre, le compte et les langues.
                val carte = liens.firstOrNull { it.selectFirst(".season-title") != null }
                    ?: liens.first()

                Groupe(
                    slug = slug,
                    titre = carte.selectFirst(".season-title")?.text()
                        ?.takeIf { it.isNotBlank() }
                        ?: titreDepuisSlug(slug),
                    url = page.newBuilder()
                        .encodedPath("/$slugFiche/$slug/")
                        .query(null)
                        .fragment(null)
                        .build()
                        .toString(),
                    nombreEpisodes = carte.selectFirst(".season-meta")?.text()
                        ?.let { NOMBRE_EPISODES.find(it) }
                        ?.groupValues?.get(1)?.toIntOrNull(),
                    langues = carte.select(".season-languages .lang-badge").langues()
                )
            }

        // Comme le script : on ne reordonne que des saisons numerotees. Des sagas,
        // des films et des hors-series gardent l'ordre que la fiche leur donne.
        return if (groupes.all { it.type == "saison" }) {
            groupes.sortedBy { it.numero ?: Int.MAX_VALUE }
        } else {
            groupes
        }
    }

    fun parseEpisodes(html: String, urlGroupe: String): List<EpisodeListe> {
        val page = urlGroupe.toHttpUrlOrNull() ?: return emptyList()
        val chemin = page.segments()
        if (chemin.size < 2) return emptyList()

        return Jsoup.parse(html)
            .select("a[href]")
            .mapNotNull { lien ->
                val cible = page.resolve(lien.attr("href")) ?: return@mapNotNull null
                val segments = cible.segments()
                if (cible.host != page.host || segments.size <= chemin.size) return@mapNotNull null
                if (segments.take(chemin.size) != chemin) return@mapNotNull null

                val numero = SEGMENT_EPISODE.matchEntire(segments[chemin.size])
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@mapNotNull null

                // `data-langs` dit toutes les langues de l'episode ; le lien, lui,
                // n'en porte qu'une, celle vers laquelle il pointe.
                val langues = lien.attr("data-langs").lowercase().split(' ', ',') +
                    listOfNotNull(segments.getOrNull(chemin.size + 1)?.lowercase())

                EpisodeListe(
                    numero = numero,
                    url = cible.toString(),
                    langues = langues.filter { it.isNotBlank() }.distinct()
                )
            }
            .groupBy { it.numero }
            .map { (_, memes) ->
                memes.first().copy(langues = memes.flatMap { it.langues }.distinct())
            }
            .sortedBy { it.numero }
    }

    fun parseLecteurs(html: String): Map<String, List<String>> {
        val declares = objetJson(LECTEURS, html)
            ?.mapValues { (_, valeur) ->
                (valeur as? JsonArray).orEmpty().mapNotNull { it.texte() }.distinct()
            }
            ?.filterValues { it.isNotEmpty() }
            .orEmpty()

        return declares.ifEmpty { cadres(html) }
    }

    fun parseFilm(html: String): Map<String, List<String>> {
        val declares = objetJson(FILM, html)
            ?.mapNotNull { (langue, valeur) -> valeur.texte()?.let { langue to listOf(it) } }
            ?.toMap()
            .orEmpty()

        return declares.ifEmpty { cadres(html) }
    }

    /** Repli du script : les lecteurs poses directement en `<iframe>`. */
    private fun cadres(html: String): Map<String, List<String>> {
        val adresses = Jsoup.parse(html)
            .select("iframe")
            .map { it.attr("src").ifBlank { it.attr("data-src") } }
            .filter { it.startsWith("http") }
            .distinct()

        return if (adresses.isEmpty()) emptyMap() else mapOf(LANGUE_PAR_DEFAUT to adresses)
    }

    private fun objetJson(motif: Regex, html: String): JsonObject? {
        val brut = motif.find(html)?.groupValues?.get(1) ?: return null
        return runCatching { Json.parseToJsonElement(brut) as? JsonObject }.getOrNull()
    }

    private suspend fun telecharger(url: String): String = withContext(Dispatchers.IO) {
        val requete = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", baseUrl)
            .get()
            .build()

        client.newCall(requete).await().use { reponse ->
            if (!reponse.isSuccessful) {
                throw DataException.CatalogueUnavailable(
                    "Page indisponible (${reponse.code}) : $url"
                )
            }
            reponse.body?.string().orEmpty()
        }
    }

    private fun racine(): HttpUrl = baseUrl.toHttpUrlOrNull()
        ?: throw DataException.CatalogueUnavailable("BASE_URL_LIS n'est pas une adresse valide")

    companion object {
        /** Clef des lecteurs trouves hors de toute declaration de langue. */
        const val LANGUE_PAR_DEFAUT = "default"

        private const val CHEMIN_RECHERCHE = "/template-php/defaut/fetch.php"

        private val SEGMENT_EPISODE = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE)
        private val NUMERO_GROUPE =
            Regex("""^(?:(?:kai-)?saga|saison)-(\d+)""", RegexOption.IGNORE_CASE)
        private val NOMBRE_EPISODES = Regex("""(\d+)\s*[ée]pisodes?""", RegexOption.IGNORE_CASE)
        private val LECTEURS = Regex("""\bconst\s+readers\s*=\s*(\{[\s\S]*?\})\s*;""")
        private val FILM = Regex("""\bconst\s+filmUrls\s*=\s*(\{[\s\S]*?\})\s*;""")

        private fun HttpUrl.segments(): List<String> = pathSegments.filter { it.isNotEmpty() }

        private fun Iterable<Element>.langues(): List<String> =
            map { it.text().trim().lowercase() }.filter { it.isNotBlank() }.distinct()

        private fun kotlinx.serialization.json.JsonElement.texte(): String? =
            (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

        /** `saison-1` donne « Saison 1 », quand la fiche ne nomme pas le groupe. */
        private fun titreDepuisSlug(slug: String): String =
            slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}
