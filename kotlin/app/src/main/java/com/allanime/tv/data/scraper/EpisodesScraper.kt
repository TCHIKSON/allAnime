package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Lecteur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Port Kotlin de `scripts/episode.js` (section 12 du cahier des charges).
 *
 * Le script de reference telecharge `<saison>/<langue>/episodes.js`, l'execute dans
 * un contexte JavaScript et lit les variables `eps1`, `eps2`, ... Chaque variable est
 * un lecteur different, et la case `i` de chaque tableau est l'episode `i + 1`.
 *
 * Android ne dispose pas d'un interpreteur JavaScript hors WebView : on lit donc les
 * memes tableaux par analyse syntaxique du fichier, ce qui est deterministe et evite
 * d'evaluer du code distant. La structure produite est identique a celle du
 * `episode.json` du script : `Episode 1 -> [url, url, ...]`.
 */
class EpisodesScraper(
    private val client: OkHttpClient,
    private val userAgent: String = AppConfig.scraperUserAgent
) {

    /**
     * @param seasonLanguageUrl URL absolue d'une saison dans une langue,
     *   par exemple `https://exemple.tld/catalogue/mon-anime/saison1/vostfr`.
     */
    suspend fun fetch(seasonLanguageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val scriptUrl = seasonLanguageUrl.trimEnd('/') + "/" + EPISODES_SCRIPT

        val request = Request.Builder()
            .url(scriptUrl)
            .header("User-Agent", userAgent)
            .get()
            .build()

        val script = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw DataException.EpisodesUnavailable(
                    "episodes.js indisponible (${response.code}) pour $seasonLanguageUrl"
                )
            }
            response.body?.string().orEmpty()
        }

        parse(script).ifEmpty {
            throw DataException.EpisodesUnavailable(
                "Aucun lecteur declare dans episodes.js pour $seasonLanguageUrl"
            )
        }
    }

    /**
     * Transforme le contenu de `episodes.js` en liste d'episodes.
     *
     * Visible pour les tests : c'est la partie sensible du port.
     */
    fun parse(script: String): List<Episode> {
        // Un tableau par lecteur, ordonne par numero de variable (eps1, eps2, ...).
        val numerotes = EPS_NUMEROTE.findAll(script)
            .mapNotNull { match ->
                val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val urls = urls(match.groupValues[2])
                if (urls.isEmpty()) null else index to urls
            }
            .sortedBy { it.first }
            .map { it.second }
            .toList()

        // Le fichier declare aussi un tableau `epsAS` de fichiers video directs, que
        // le script de reference ignore. Il reste ignore ici : ces URL pointent vers
        // le domaine historique `anime-sama.fr`, qui ne resout plus depuis la bascule
        // du site. Les proposer reviendrait a offrir un lecteur toujours en echec.
        val lecteursParVariable = numerotes

        if (lecteursParVariable.isEmpty()) return emptyList()

        val nombreEpisodes = lecteursParVariable.maxOf { it.size }

        return (0 until nombreEpisodes).map { position ->
            // Les adresses amputees de leur identifiant sont ecartees ici : plus
            // bas, un episode qui n'a plus aucun lecteur sera simplement absent,
            // ce qui est exactement ce qu'il faut dire d'une saison-gabarit.
            val lecteurs = lecteursParVariable
                .mapNotNull { it.getOrNull(position) }
                .map { url -> Lecteur(index = 0, url = url) }
                .filter { it.estExploitable }
                .mapIndexed { ordre, lecteur -> lecteur.copy(index = ordre + 1) }

            Episode(numero = position + 1, lecteurs = lecteurs)
        }
    }

    /** Extrait les URL entre guillemets simples ou doubles d'un corps de tableau. */
    private fun urls(corpsDuTableau: String): List<String> =
        URL_LITERAL.findAll(corpsDuTableau)
            .map { it.groupValues[1].trim() }
            .toList()

    private companion object {
        const val EPISODES_SCRIPT = "episodes.js"

        /** `var eps12 = [ ... ];` : un lecteur tiers par variable numerotee. */
        val EPS_NUMEROTE = Regex("""\beps(\d+)\s*=\s*\[([^\]]*)]""")

        /** Une URL entre guillemets simples ou doubles. */
        val URL_LITERAL = Regex("""['"]\s*(https?://[^'"]+?)\s*['"]""")
    }
}
