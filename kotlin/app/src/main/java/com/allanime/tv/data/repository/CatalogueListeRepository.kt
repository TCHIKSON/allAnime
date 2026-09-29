package com.allanime.tv.data.repository

import android.util.Log
import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.scraper.CatalogueListeScraper
import com.allanime.tv.domain.model.AnimeCatalogue
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * La liste du catalogue, source de l'accueil.
 *
 * L'accueil ne part plus des tendances AniList mais de ce que le site propose
 * reellement : on telecharge la page `/catalogue` filtree sur les animes, puis les
 * pages suivantes annoncees par sa pagination.
 *
 * Pourquoi plusieurs pages : la liste est triee par titre. Se limiter a la premiere
 * page donnerait un accueil entierement compose de titres commencant par « A », et
 * les rangees par genre n'auraient plus de sens. Le nombre de pages reste plafonne
 * par [MAX_PAGES] : au-dela, c'est la recherche qui prend le relais, plutot que de
 * faire telecharger un annuaire complet a un televiseur.
 *
 * Le resultat est garde en memoire pour la duree du processus : l'accueil, la fiche
 * et le planning y puisent sans relancer le moindre telechargement.
 */
class CatalogueListeRepository(
    private val scraper: CatalogueListeScraper
) {

    private val verrou = Mutex()
    private val parSlug = ConcurrentHashMap<String, AnimeCatalogue>()

    @Volatile
    private var cache: List<AnimeCatalogue>? = null

    /** Catalogue complet, telecharge au premier appel ; [rafraichir] force une relecture. */
    suspend fun animes(rafraichir: Boolean = false): List<AnimeCatalogue> {
        if (!rafraichir) cache?.let { return it }

        return verrou.withLock {
            if (!rafraichir) cache?.let { return@withLock it }

            val resultat = telecharger()
            resultat.forEach { parSlug[it.slug] = it }
            cache = resultat
            resultat
        }
    }

    /**
     * Entree deja telechargee pour ce slug, ou `null`.
     *
     * Sert a la fiche, qui n'a que le slug en main apres une navigation : elle evite
     * ainsi de retelecharger le catalogue pour retrouver le titre et la jaquette.
     */
    fun parSlug(slug: String): AnimeCatalogue? = parSlug[slug]

    private suspend fun telecharger(): List<AnimeCatalogue> = coroutineScope {
        val premiere = scraper.fetch(AppConfig.catalogueListeUrl(1))
        val total = premiere.nombrePages.coerceAtMost(MAX_PAGES)

        if (total <= 1) return@coroutineScope premiere.animes

        val limite = Semaphore(MAX_REQUETES_PARALLELES)
        val suivantes = (2..total)
            .map { numero ->
                async {
                    // Une page manquante ne doit pas vider l'accueil : on garde ce
                    // qui a pu etre lu et on note l'echec.
                    runCatchingCancellable {
                        limite.withPermit { scraper.fetch(AppConfig.catalogueListeUrl(numero)) }
                    }
                        .onFailure { Log.w(TAG, "Page $numero du catalogue illisible", it) }
                        .getOrNull()
                        ?.animes
                        .orEmpty()
                }
            }
            .awaitAll()
            .flatten()

        (premiere.animes + suivantes).distinctBy { it.slug }
    }

    private companion object {
        const val TAG = "CatalogueListeRepository"

        /** Plafond de pages telechargees pour composer l'accueil. */
        const val MAX_PAGES = 10

        /** Le catalogue tient sur un serveur modeste : on ne le sature pas. */
        const val MAX_REQUETES_PARALLELES = 4
    }
}
