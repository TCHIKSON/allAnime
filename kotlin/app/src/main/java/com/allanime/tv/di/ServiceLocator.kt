package com.allanime.tv.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.allanime.tv.data.anilist.AniListService
import com.allanime.tv.data.historique.HistoriqueRepository
import com.allanime.tv.data.lecteur.ClassementLecteurs
import com.allanime.tv.data.net.HttpClients
import com.allanime.tv.data.planning.PlanningRepository
import com.allanime.tv.data.repository.AnimeRepository
import com.allanime.tv.data.repository.CatalogueListeRepository
import com.allanime.tv.data.repository.CatalogueRepository
import com.allanime.tv.data.scraper.CatalogueListeScraper
import com.allanime.tv.data.scraper.EpisodesScraper
import com.allanime.tv.data.scraper.LisScraper
import com.allanime.tv.data.scraper.PlanningScraper
import com.allanime.tv.data.scraper.RechercheCatalogueScraper
import com.allanime.tv.data.scraper.SaisonsScraper
import com.allanime.tv.data.scraper.SlugResolver

/** Stockage de l'historique, rattache au contexte applicatif. */
private val Context.historiqueDataStore by preferencesDataStore(name = "historique")

/** Stockage du classement des hebergeurs de lecteurs. */
private val Context.lecteursDataStore by preferencesDataStore(name = "lecteurs")

/** Stockage du planning hebdomadaire, pour un accueil qui n attend pas le reseau. */
private val Context.planningDataStore by preferencesDataStore(name = "planning")

/**
 * Assemblage manuel des dependances.
 *
 * L'application est assez petite pour ne pas justifier un conteneur d'injection :
 * un point d'assemblage unique suffit et garde les ViewModels testables (leurs
 * dependances restent passees par constructeur).
 *
 * [initialiser] doit etre appele une fois au demarrage, depuis la classe
 * `Application`, car l'historique a besoin d'un contexte.
 */
object ServiceLocator {

    private lateinit var contexteApplicatif: Context

    fun initialiser(context: Context) {
        contexteApplicatif = context.applicationContext
    }

    private val aniListService by lazy { AniListService(HttpClients.shared) }

    private val episodesScraper by lazy { EpisodesScraper(HttpClients.shared) }

    private val saisonsScraper by lazy { SaisonsScraper(HttpClients.shared, episodesScraper) }

    private val lisScraper by lazy { LisScraper(HttpClients.shared) }

    private val rechercheCatalogueScraper by lazy {
        RechercheCatalogueScraper(HttpClients.shared)
    }

    private val slugResolver by lazy {
        SlugResolver(HttpClients.shared, rechercheCatalogueScraper)
    }

    private val catalogueListeScraper by lazy { CatalogueListeScraper(HttpClients.shared) }

    private val planningScraper by lazy { PlanningScraper(HttpClients.shared) }

    val animeRepository: AnimeRepository by lazy { AnimeRepository(aniListService) }

    val catalogueRepository: CatalogueRepository by lazy {
        CatalogueRepository(saisonsScraper, episodesScraper, slugResolver, lisScraper)
    }

    /** Liste du catalogue : la source de l accueil. */
    val catalogueListeRepository: CatalogueListeRepository by lazy {
        CatalogueListeRepository(catalogueListeScraper)
    }

    val planningRepository: PlanningRepository by lazy {
        PlanningRepository(contexteApplicatif.planningDataStore, planningScraper)
    }

    val historiqueRepository: HistoriqueRepository by lazy {
        HistoriqueRepository(contexteApplicatif.historiqueDataStore)
    }

    val classementLecteurs: ClassementLecteurs by lazy {
        ClassementLecteurs(contexteApplicatif.lecteursDataStore)
    }
}
