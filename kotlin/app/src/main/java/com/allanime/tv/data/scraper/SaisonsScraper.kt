package com.allanime.tv.data.scraper

import android.util.Log
import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.LangueCode
import com.allanime.tv.domain.model.Saison
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Port Kotlin de `scripts/saisson.js` (sections 9 et 10 du cahier des charges).
 *
 * Deroule la meme sequence que le script de reference :
 *  1. telecharger la fiche catalogue de l'anime ;
 *  2. y lire les appels `panneauAnime("Saison 1", "saison1/vostfr")` ;
 *  3. pour chaque saison, sonder les suffixes de langue et ne garder que ceux qui
 *     repondent 200 ;
 *  4. compter les episodes via `episodes.js`.
 *
 * Deux ecarts assumes par rapport au script Node, signales sur place :
 *  - le gabarit `panneauAnime("nom", "url")` present dans la page est ignore ;
 *  - les sondages de langue utilisent HEAD (repli GET) et sont paralleles, pour ne
 *    pas telecharger neuf pages completes par saison sur une TV.
 */
class SaisonsScraper(
    private val client: OkHttpClient,
    private val episodesScraper: EpisodesScraper,
    private val userAgent: String = AppConfig.scraperUserAgent
) {

    /**
     * @param catalogueUrl URL de la fiche, terminee par `/`,
     *   par exemple `https://exemple.tld/catalogue/classroom-of-the-elite/`.
     */
    suspend fun fetch(catalogueUrl: String): List<Saison> = coroutineScope {
        val html = downloadCatalogue(catalogueUrl)
        val panneaux = parsePanneaux(html)

        if (panneaux.isEmpty()) {
            throw DataException.CatalogueUnavailable(
                "Aucun panneau de saison exploitable sur $catalogueUrl"
            )
        }

        val limite = Semaphore(MAX_REQUETES_PARALLELES)

        val sondees = panneaux
            .map { panneau ->
                async { buildSaison(catalogueUrl, panneau, limite) }
            }
            .awaitAll()

        retenir(sondees)
    }

    /**
     * Ne garde que les saisons qui menent quelque part.
     *
     * **La page ment.** Elle est batie sur un gabarit que les editeurs recopient, et
     * beaucoup de series declarent « Saison 1 » a « Saison 10 », « Film » et « OAV »
     * alors qu'une seule existe. Les panneaux ne sont pas en commentaire : ils sont
     * bel et bien appeles, le script de reference les prend donc tous, et
     * l'application affichait dix onglets pour une serie qui n'a qu'une saison.
     *
     * On ne peut pas trancher a la lecture de la page : il faut regarder ce que
     * chaque saison contient. Trois cas la disqualifient :
     *
     *  - **aucune langue** : les episodes ne sont atteignables qu'a l'adresse
     *    `<saison>/<langue>`, une saison sans langue n'a donc aucun contenu ;
     *  - **aucun episode exploitable** : la page existe mais ne declare que des
     *    adresses amputees de leur identifiant, ou rien du tout ;
     *  - **la recopie d'une saison precedente** : le site sert le meme `episodes.js`
     *    a plusieurs adresses, et la « Saison 2 » est alors la Saison 1.
     *
     * Une saison dont la lecture a **echoue** est conservee : on ne retire que ce
     * dont on a la preuve. Une panne reseau ne doit pas faire disparaitre une saison.
     */
    private fun retenir(sondees: List<SaisonSondee>): List<Saison> {
        val exploitables = sondees.filter { (saison, empreinte) ->
            saison.langues.isNotEmpty() && empreinte?.isEmpty() != true
        }

        // Les recopies se reconnaissent a leur **premier episode** : le site sert le
        // meme `episodes.js` a plusieurs adresses, parfois tronque — la « Saison 2 »
        // de certaines series est la Saison 1 arretee au dixieme episode. Comparer
        // les listes entieres ne l'aurait pas vu ; comparer le premier episode, si.
        //
        // Quand plusieurs saisons partagent ce debut, on garde **la plus fournie** :
        // c'est l'originale, la copie etant par construction tronquee.
        val laPlusFournie = exploitables
            .filter { it.empreinte != null }
            .groupBy { it.empreinte?.firstOrNull() }
            .mapValues { (_, groupe) -> groupe.maxBy { it.empreinte?.size ?: 0 } }

        return exploitables.mapNotNull { sondee ->
            when {
                // Illisible : on ne peut rien prouver, on la garde.
                sondee.empreinte == null -> sondee.saison
                laPlusFournie[sondee.empreinte.firstOrNull()] === sondee -> sondee.saison
                else -> null
            }
        }
    }

    /** Une saison et l'empreinte de ses episodes, ou `null` si elle est illisible. */
    private data class SaisonSondee(
        val saison: Saison,
        val empreinte: List<List<String>>?
    )

    private suspend fun downloadCatalogue(catalogueUrl: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(catalogueUrl)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw DataException.CatalogueUnavailable(
                        "Fiche catalogue indisponible (${response.code}) : $catalogueUrl"
                    )
                }
                response.body?.string().orEmpty()
            }
        }

    /**
     * Extrait les appels `panneauAnime(...)` de la page.
     *
     * Visible pour les tests : c'est la partie sensible du port.
     */
    fun parsePanneaux(html: String): List<PanneauSaison> {
        val document = Jsoup.parse(html)

        // Le script de reference ne regarde que cette div ; on elargit au document
        // entier si la classe change, plutot que de renvoyer une liste vide.
        val conteneur = document.select(SELECTEUR_CONTENEUR)
        val scripts = if (conteneur.isNotEmpty()) {
            conteneur.select("script")
        } else {
            document.select("script")
        }

        val contenu = scripts.joinToString("\n") { it.data() }

        return PANNEAU_ANIME.findAll(contenu)
            .map { match -> PanneauSaison(nom = match.groupValues[1], path = match.groupValues[2]) }
            .filterNot { it.estUnGabarit }
            .distinctBy { it.path }
            .toList()
    }

    private suspend fun buildSaison(
        catalogueUrl: String,
        panneau: PanneauSaison,
        limite: Semaphore
    ): SaisonSondee = coroutineScope {
        val racine = panneau.racine
        val baseUrl = resolve(catalogueUrl, racine)

        val languesDifferees = LangueCode.entries.map { code ->
            async {
                val cible = "$baseUrl/${code.pathSegment}"
                if (limite.withPermit { existe(cible) }) {
                    Langue(code = code, path = "$racine/${code.pathSegment}")
                } else {
                    null
                }
            }
        }

        // Les episodes du chemin annonce par le panneau, comme dans le script de
        // reference. Ils servent a deux choses : compter, et reconnaitre une saison
        // qui n'est que la recopie d'une autre.
        val episodesDifferes = async {
            try {
                limite.withPermit { episodesScraper.fetch(resolve(catalogueUrl, panneau.path)) }
            } catch (vide: DataException.EpisodesUnavailable) {
                // La page repond mais ne declare aucun lecteur, ou n'existe pas :
                // dans les deux cas la saison n'a rien a offrir, et c'est une reponse,
                // pas une panne.
                Log.i(TAG, "Saison sans episode : ${panneau.path} (${vide.message})")
                emptyList()
            } catch (panne: Exception) {
                if (panne is kotlinx.coroutines.CancellationException) throw panne
                Log.w(TAG, "Episodes illisibles pour ${panneau.path}", panne)
                null
            }
        }

        // Seuls les episodes qui ont encore un lecteur comptent : une saison-gabarit
        // en declare un, sans aucune adresse exploitable.
        val episodes = episodesDifferes.await()?.filter { it.estDisponible }

        SaisonSondee(
            saison = Saison(
                nom = panneau.nom,
                path = panneau.path,
                baseUrl = baseUrl,
                nombreEpisodes = episodes?.size,
                langues = languesDifferees.awaitAll().filterNotNull()
            ),
            empreinte = episodes?.map { episode -> episode.lecteurs.map { it.url } }
        )
    }

    /**
     * Teste l'existence d'une page de langue. Seul un 200 vaut disponibilite, comme
     * dans le script de reference ; HEAD evite de telecharger la page entiere et on
     * repasse en GET si le serveur refuse la methode.
     */
    private suspend fun existe(url: String): Boolean = withContext(Dispatchers.IO) {
        val head = requete(url, "HEAD")
        when {
            head == 200 -> true
            head == 405 || head == 501 -> requete(url, "GET") == 200
            else -> false
        }
    }

    private suspend fun requete(url: String, methode: String): Int? = runCatchingCancellable {
        val builder = Request.Builder()
            .header("User-Agent", userAgent)
            .url(url)
        val request = if (methode == "HEAD") builder.head().build() else builder.get().build()
        client.newCall(request).await().use { it.code }
    }.getOrNull()

    /** Resout un chemin relatif contre l'URL de la fiche catalogue. */
    private fun resolve(catalogueUrl: String, path: String): String =
        URI(catalogueUrl).resolve(path).toString().trimEnd('/')

    /** Un appel `panneauAnime("Saison 1", "saison1/vostfr")` lu dans la page. */
    data class PanneauSaison(
        val nom: String,
        val path: String
    ) {
        /**
         * La page du catalogue contient l'appel d'exemple `panneauAnime("nom", "url")`
         * qui sert de gabarit. Le script Node le laisse passer et produit une saison
         * fantome ; on l'ecarte ici.
         */
        val estUnGabarit: Boolean
            get() = nom.equals("nom", ignoreCase = true) && path.equals("url", ignoreCase = true)

        /** Chemin de la saison sans le segment de langue : `saison1/vostfr` donne `saison1`. */
        val racine: String
            get() = if (path.contains('/')) path.substringBeforeLast('/') else path
    }

    private companion object {
        const val TAG = "SaisonsScraper"
        const val MAX_REQUETES_PARALLELES = 6
        const val SELECTEUR_CONTENEUR = "div.flex.flex-wrap.overflow-y-hidden"

        val PANNEAU_ANIME =
            Regex("""panneauAnime\s*\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""")
    }
}
