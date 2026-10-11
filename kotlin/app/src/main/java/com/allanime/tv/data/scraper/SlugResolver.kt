package com.allanime.tv.data.scraper

import android.util.Log
import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.Slugifier
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.CorrespondanceCatalogue
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Determine le slug reellement utilise par le catalogue pour un anime donne.
 *
 * Le catalogue ne suit pas une convention unique : `shingeki-no-kyojin` est en
 * romaji, `classroom-of-the-elite` en anglais, `demon-slayer` et `frieren` sont des
 * noms courts. Deux moyens sont employes, dans cet ordre :
 *
 *  1. **la confirmation par le moteur de recherche du site**, qui repond a un titre
 *     par les fiches qu'il connait, titres alternatifs compris. Quand l'une d'elles
 *     correspond, son slug est celui du site lui-meme : il n'y a plus rien a
 *     deviner. C'est `CorrespondanceCatalogue` qui tranche ;
 *  2. **le sondage**, si la recherche n'a rien confirme : les titres candidats sont
 *     slugifies et l'on retient le premier dont la fiche repond 200.
 *
 * Les candidats viennent de `Anime.candidatsCatalogue` : romaji d'abord, anglais en
 * repli, puis synonymes et formes raccourcies. Chacun est decline par
 * `Slugifier.variantes`, l'apostrophe pouvant y etre effacee ou separatrice.
 */
class SlugResolver(
    private val client: OkHttpClient,
    private val rechercheScraper: RechercheCatalogueScraper,
    private val userAgent: String = AppConfig.scraperUserAgent
) {

    private val titresParSlug = ConcurrentHashMap<String, List<String>>()

    /**
     * Titres sous lesquels le catalogue connait la fiche [slug], quand c'est son
     * moteur de recherche qui l'a confirmee : son titre, puis ses titres alternatifs.
     *
     * Vide pour un slug obtenu par sondage, dont on ne connait que l'adresse.
     */
    fun titres(slug: String): List<String> = titresParSlug[slug].orEmpty()

    /**
     * @param candidats titres a essayer, du plus probable au moins probable.
     * @return le premier slug dont la fiche existe, ou `null` si aucun ne repond.
     */
    suspend fun resoudre(candidats: List<String>): String? = withContext(Dispatchers.IO) {
        confirmer(candidats)?.let { return@withContext it }

        // Chaque titre donne ses deux formes de slug — l'apostrophe effacee, puis
        // l'apostrophe separatrice — essayees l'une apres l'autre avant de passer au
        // titre suivant : le plus probable reste explore en premier. Un titre sans
        // apostrophe n'en produit qu'une, la liste ne s'allonge donc que la ou le
        // catalogue hesite reellement.
        val slugs = candidats
            .flatMap(Slugifier::variantes)
            .distinct()

        for (slug in slugs) {
            if (existe(slug)) {
                Log.i(TAG, "Slug retenu : $slug")
                return@withContext slug
            }
        }

        Log.w(TAG, "Aucun slug trouve parmi : ${slugs.joinToString()}")
        null
    }

    /**
     * Interroge le moteur de recherche du site avec les premiers titres candidats.
     *
     * Le nombre d'appels est borne : chacun est un aller-retour reseau, et les
     * candidats suivants sont des variantes de plus en plus lointaines, que le
     * moteur aurait de toute facon retrouvees par les titres alternatifs.
     *
     * Une recherche indisponible n'interrompt rien : le sondage prend la suite.
     */
    private suspend fun confirmer(candidats: List<String>): String? {
        for (titre in candidats.take(MAX_RECHERCHES)) {
            val resultats = runCatchingCancellable { rechercheScraper.rechercher(titre) }
                .onFailure { erreur -> Log.w(TAG, "Recherche indisponible : $titre", erreur) }
                .getOrNull()
                ?: continue

            // Une forme raccourcie (« THE LAST » pour « THE LAST: NARUTO THE MOVIE »)
            // ne vaut que si une fiche porte exactement ce nom : la chercher dans un
            // titre plus long ramenerait la premiere serie qui commence pareil.
            val abrege = candidats.any { autre ->
                autre.length > titre.length && autre.startsWith(titre, ignoreCase = true)
            }
            val retenu = if (abrege) {
                CorrespondanceCatalogue.choisirExact(titre, resultats)
            } else {
                CorrespondanceCatalogue.choisir(titre, resultats)
            } ?: continue
            Log.i(TAG, "Slug confirme par la recherche : ${retenu.slug}")
            titresParSlug[retenu.slug] = listOf(retenu.titre) + retenu.sousTitres
            return retenu.slug
        }

        return null
    }

    private suspend fun existe(slug: String): Boolean {
        val url = AppConfig.catalogueUrl(slug)

        val code = runCatchingCancellable {
            val requete = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .head()
                .build()
            client.newCall(requete).await().use { it.code }
        }.getOrNull()

        // Certains serveurs refusent HEAD : on retente en GET avant de conclure.
        return when (code) {
            200 -> true
            405, 501 -> runCatchingCancellable {
                val requete = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
                client.newCall(requete).await().use { it.code }
            }.getOrNull() == 200

            else -> false
        }
    }

    private companion object {
        const val TAG = "SlugResolver"

        /** Titres soumis au moteur de recherche avant de passer au sondage. */
        const val MAX_RECHERCHES = 3
    }
}
