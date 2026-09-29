package com.allanime.tv.data.scraper

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.LienCatalogue
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.JourPlanning
import com.allanime.tv.domain.model.JourSemaine
import com.allanime.tv.domain.model.Planning
import com.allanime.tv.domain.model.SortiePlanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Port Kotlin de `lecteur-videos/parserPlanning.js`.
 *
 * Le script de reference parcourt les sept colonnes de `#planningClass` et transforme
 * chaque `.planning-card` en objet JSON. Les selecteurs sont repris tels quels ;
 * quatre points different, et chacun corrige une approximation du script :
 *
 *  - le titre retenu est celui de `.card-title`, correctement capitalise, plutot que
 *    l'attribut `data-title` qui est en minuscules parce qu'il sert au filtrage ;
 *  - la precision de saison est lue sans le bloc d'heure. Le script selectionne
 *    `.info-item.episode .info-text`, or le bloc d'heure porte lui aussi la classe
 *    `episode` : il produit donc « 18h30Saison 1 » ;
 *  - l'heure `?` affichee par le site quand aucun horaire n'est arrete devient
 *    `null`, un episode sans heure et un episode a minuit n'etant pas la meme chose ;
 *  - la liste des oeuvres sans jour fixe, que le script ignore, est recuperee.
 */
class PlanningScraper(
    private val client: OkHttpClient,
    private val userAgent: String = AppConfig.scraperUserAgent,
    private val segmentCatalogue: String = AppConfig.segmentCatalogue
) {

    /** Telecharge et analyse la page de planning. */
    suspend fun fetch(url: String = AppConfig.planningUrl): Planning {
        val html = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw DataException.CatalogueUnavailable(
                        "Planning indisponible (${response.code}) : $url"
                    )
                }
                response.body?.string().orEmpty()
            }
        }

        return parse(html)
    }

    /**
     * Transforme le HTML du planning en modele.
     *
     * Visible pour les tests : c'est la partie sensible du port.
     *
     * @param horodatage date de telechargement, en millisecondes.
     */
    fun parse(html: String, horodatage: Long = System.currentTimeMillis()): Planning {
        val document = Jsoup.parse(html)

        val colonnes = document.select(COLONNES)
        if (colonnes.isEmpty()) {
            throw DataException.CatalogueUnavailable("Aucune colonne de planning exploitable")
        }

        val jours = colonnes.mapIndexedNotNull { rang, colonne ->
            // Le libelle fait foi ; l'ordre des colonnes sert de repli si le site
            // change sa maniere d'ecrire les jours.
            val jour = JourSemaine.depuisLibelle(colonne.selectFirst(TITRE_JOUR)?.text())
                ?: JourSemaine.entries.getOrNull(rang)
                ?: return@mapIndexedNotNull null

            JourPlanning(
                jour = jour,
                date = colonne.selectFirst("p")?.text()?.trim().orEmpty(),
                // Un meme titre revient legitimement plusieurs fois par jour, une
                // fois par langue ; deux cartes rigoureusement identiques, en
                // revanche, sont une redite du site et casseraient les clefs des
                // listes paresseuses.
                sorties = colonne.select(CARTE).mapNotNull(::versSortie).distinctBy { it.cle }
            )
        }
            // Une colonne en double ne doit pas dedoubler la rangee « Sorties du jour ».
            .distinctBy { it.jour }

        return Planning(
            jours = jours,
            sansJourFixe = document.select(SANS_JOUR_FIXE)
                .mapNotNull(::versSortie)
                .distinctBy { it.cle },
            recupereLe = horodatage
        )
    }

    private fun versSortie(carte: Element): SortiePlanning? {
        val slug = LienCatalogue.slug(carte.selectFirst("a")?.attr("href"), segmentCatalogue)
            ?: return null

        val titre = carte.selectFirst(TITRE_CARTE)?.text()?.trim()
            ?: carte.attr(ATTR_TITRE).trim()
        if (titre.isEmpty()) return null

        return SortiePlanning(
            titre = titre,
            slug = slug,
            type = carte.selectFirst(BADGE_TYPE)?.text()?.trim().orEmpty(),
            langue = carte.selectFirst(DRAPEAU)?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() },
            image = carte.selectFirst(IMAGE)?.attr("src")?.trim()?.takeIf { it.isNotEmpty() },
            heure = heure(carte),
            precision = carte.selectFirst(PRECISION)?.text()?.trim()?.takeIf { it.isNotEmpty() },
            reporte = carte.selectFirst(REPORTE) != null,
            horodatage = carte.attr(ATTR_HORODATAGE).trim().toLongOrNull()
        )
    }

    /** `18h30` tel quel ; `?` — horaire non arrete par le site — devient `null`. */
    private fun heure(carte: Element): String? =
        carte.selectFirst(HEURE)
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != HEURE_INCONNUE }

    private companion object {
        const val COLONNES = "#planningClass > div"
        const val TITRE_JOUR = ".titreJours"
        const val CARTE = ".planning-card"
        const val TITRE_CARTE = ".card-title"
        const val ATTR_TITRE = "data-title"
        const val ATTR_HORODATAGE = "data-release-ts"
        const val BADGE_TYPE = ".anime-badge .badge-text, .scan-badge .badge-text"
        const val DRAPEAU = ".language-badge-top .flag-icon"
        const val IMAGE = ".card-image"
        const val HEURE = ".planning-time"
        const val PRECISION = ".info-item.episode:not(.planning-time) .info-text"
        const val REPORTE = ".planning-problem-red"
        const val SANS_JOUR_FIXE = ".scrollBarStyled .planning-card"

        /** Ce que le site affiche a la place d'une heure qu'il ne connait pas. */
        const val HEURE_INCONNUE = "?"
    }
}
