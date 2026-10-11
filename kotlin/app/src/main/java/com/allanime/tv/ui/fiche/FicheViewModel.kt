package com.allanime.tv.ui.fiche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.core.dataOrNull
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.historique.HistoriqueRepository
import com.allanime.tv.data.repository.AnimeRepository
import com.allanime.tv.data.repository.CatalogueListeRepository
import com.allanime.tv.data.repository.CatalogueRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.LangueCode
import com.allanime.tv.domain.model.ProgressionAnime
import com.allanime.tv.domain.model.Saison
import com.allanime.tv.domain.model.versAnime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Fiche detaillee d'un anime.
 *
 * La fiche porte desormais aussi la selection saison / langue / episode : c'est la
 * structure des interfaces TV grand public, ou la liste des episodes fait partie de
 * la page de la serie plutot que d'un ecran separe.
 *
 * Les chargements restent independants : une fiche reste consultable meme si le
 * catalogue est injoignable ; c'est seulement l'acces aux episodes qui est alors
 * indisponible.
 */
data class EtatFiche(
    val fiche: UiState<Anime> = UiState.Loading(R.string.loading_details),
    /**
     * Carte du catalogue, quand la serie a ete ouverte depuis l'accueil ou le
     * planning. Elle porte les genres du site, qui ne recouvrent pas ceux d'AniList :
     * la fiche affiche les deux, pour montrer sous quelles etiquettes l'accueil a pu
     * classer la serie.
     */
    val catalogue: AnimeCatalogue? = null,
    val saisons: UiState<List<Saison>> = UiState.Loading(R.string.loading_seasons),
    val indexSaison: Int = 0,
    val langue: Langue? = null,
    val episodes: UiState<List<Episode>> = UiState.Loading(R.string.loading_episodes),
    val progression: ProgressionAnime? = null
) {
    val saisonCourante: Saison?
        get() = saisons.dataOrNull?.getOrNull(indexSaison)

    /**
     * Langues proposees par la serie, toutes saisons confondues.
     *
     * La fiche les affiche comme des badges de disponibilite : c'est une propriete
     * de l'oeuvre, pas de la saison affichee au moment ou on la consulte.
     */
    val languesDisponibles: List<LangueCode>
        get() = saisons.dataOrNull.orEmpty()
            .flatMap { saison -> saison.langues }
            .map { it.code }
            .distinct()

    /**
     * Episode propose par le bouton principal : celui en cours si l'anime a deja ete
     * commence dans cette saison, le premier sinon.
     */
    val episodeALire: Int?
        get() {
            val disponibles = episodes.dataOrNull?.map { it.numero }.orEmpty()
            if (disponibles.isEmpty()) return null

            val repris = progression
                ?.takeIf { it.indexSaison == indexSaison && it.numeroEpisode in disponibles }
                ?.numeroEpisode

            return repris ?: disponibles.first()
        }
}

class FicheViewModel(
    private val reference: ReferenceFiche,
    private val animeRepository: AnimeRepository,
    private val catalogueRepository: CatalogueRepository,
    private val catalogueListeRepository: CatalogueListeRepository,
    private val historiqueRepository: HistoriqueRepository
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatFiche())
    val etat: StateFlow<EtatFiche> = _etat.asStateFlow()

    /** Chargement d'episodes en cours, annule des qu'une autre selection arrive. */
    private var chargementEpisodes: Job? = null

    /** Verification des langues d'une saison, annulee si l'on change de saison. */
    private var chargementSaison: Job? = null

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _etat.update { it.copy(fiche = UiState.Loading(R.string.loading_details)) }

            val resolution = runCatchingCancellable { resoudre() }
                .getOrElse { erreur ->
                    _etat.update { it.copy(fiche = UiState.Failure(erreur.toAppError())) }
                    return@launch
                }

            val anime = resolution?.fiche
            if (anime == null) {
                _etat.update { it.copy(fiche = UiState.Empty(R.string.error_not_found)) }
                return@launch
            }

            _etat.update {
                it.copy(
                    fiche = UiState.Success(anime),
                    catalogue = resolution.carte,
                    progression = historiqueRepository.progressionPour(anime.id)
                )
            }

            chargerSaisons(anime)
        }
    }

    /**
     * Retrouve la fiche a afficher, selon le chemin emprunte pour arriver ici.
     *
     * Venant du catalogue, la fiche AniList est cherchee sur le titre, exactement
     * comme le fait l'ecran de recherche : c'est cet appel qui apporte le synopsis,
     * les personnages, le staff et les notes que la carte du catalogue n'a pas. Le
     * slug, lui, est declare tout de suite au catalogue : la liste des saisons n'a
     * donc plus a sonder des titres candidats pour retrouver une page dont on connait
     * deja l'adresse.
     */
    private suspend fun resoudre(): Resolution? = when (reference) {
        is ReferenceFiche.ParId ->
            animeRepository.fiche(reference.animeId)?.let { Resolution(it, carte = null) }

        is ReferenceFiche.ParSlug -> {
            val entree = catalogueListeRepository.parSlug(reference.slug)

            // Apres un recyclage du processus, la liste du catalogue n'est plus en
            // memoire : le slug reste la seule piste, et « classroom-of-the-elite »
            // redonne un titre qu'AniList sait lire.
            val titres = entree?.titresPourRecherche
                ?: listOf(reference.slug.replace('-', ' '))

            // AniList ignore parfois un titre : la fiche se rabat alors sur ce que
            // la carte du catalogue en dit. Elle est deposee dans le cache des
            // fiches, sinon le lecteur — qui recharge par identifiant — la perdrait.
            val trouve = animeRepository.parTitres(titres)
                ?: entree?.versAnime()?.also { animeRepository.memoriser(it) }

            trouve
                ?.also {
                    catalogueRepository.associer(it.id, reference.slug)
                    // Le site de lecture retrouve la serie par son titre : on lui
                    // donne ceux que cette fiche affiche, carte du catalogue en tete.
                    catalogueRepository.declarerTitres(
                        it.id,
                        entree?.let { carte -> listOf(carte.titre) + carte.titresAlternatifs }
                            ?: titres
                    )
                }
                ?.let { Resolution(it, carte = entree) }
        }
    }

    /** Fiche AniList et, le cas echeant, la carte du catalogue dont elle est issue. */
    private data class Resolution(val fiche: Anime, val carte: AnimeCatalogue?)

    /** Relance uniquement la recuperation des saisons. */
    fun rechargerSaisons() {
        val anime = _etat.value.fiche.dataOrNull ?: return
        viewModelScope.launch { chargerSaisons(anime, rafraichir = true) }
    }

    private suspend fun chargerSaisons(anime: Anime, rafraichir: Boolean = false) {
        if (anime.candidatsCatalogue.isEmpty()) {
            _etat.update { it.copy(saisons = UiState.Empty(R.string.empty_seasons)) }
            return
        }

        _etat.update { it.copy(saisons = UiState.Loading(R.string.loading_seasons)) }

        runCatchingCancellable { catalogueRepository.saisons(anime, rafraichir) }
            .onSuccess { saisons ->
                if (saisons.isEmpty()) {
                    _etat.update { it.copy(saisons = UiState.Empty(R.string.empty_seasons)) }
                    return@onSuccess
                }
                _etat.update { it.copy(saisons = UiState.Success(saisons)) }

                // On ouvre sur la saison deja commencee, sinon sur la premiere.
                val reprise = _etat.value.progression?.indexSaison
                selectionnerSaison(reprise?.takeIf { it in saisons.indices } ?: 0)
            }
            .onFailure { erreur ->
                _etat.update { it.copy(saisons = UiState.Failure(erreur.toAppError())) }
            }
    }

    /**
     * Ouvre une saison, apres avoir verifie **quelles langues elle propose
     * reellement**.
     *
     * Le catalogue annonce parfois une VF qui n'en est pas une : la page existe, mais
     * elle sert la version originale. Seule une comparaison des episodes le revele,
     * et elle demande le reseau — d'ou le passage par une coroutine la ou la
     * selection etait immediate.
     *
     * Le travail a sa propre tache : l'annuler reviendrait a annuler le chargement
     * des episodes qu'il declenche a la fin.
     */
    fun selectionnerSaison(index: Int) {
        val saisons = _etat.value.saisons.dataOrNull ?: return
        val saison = saisons.getOrNull(index) ?: return
        val anime = _etat.value.fiche.dataOrNull ?: return

        _etat.update { it.copy(indexSaison = index, langue = null) }

        chargementSaison?.cancel()
        chargementSaison = viewModelScope.launch {
            _etat.update { it.copy(episodes = UiState.Loading(R.string.loading_episodes)) }

            // En cas d'echec, on garde ce que le catalogue annonce : mieux vaut une
            // langue de trop qu'une saison rendue injouable par une panne reseau.
            val langues = runCatchingCancellable {
                catalogueRepository.languesDistinctes(anime, saison)
            }.getOrDefault(saison.langues)

            // La saison est remplacee dans la liste pour que les pastilles affichent
            // la liste verifiee, et non celle que le catalogue pretendait.
            if (langues != saison.langues) {
                val revisees = saisons.toMutableList()
                revisees[index] = saison.copy(langues = langues)
                _etat.update { it.copy(saisons = UiState.Success(revisees)) }
            }

            val progression = _etat.value.progression
            val langue = langues
                .firstOrNull {
                    progression?.indexSaison == index && it.code.code == progression.codeLangue
                }
                ?: langues.firstOrNull { it.code == LangueCode.VOSTFR }
                ?: langues.firstOrNull()

            if (langue == null) {
                chargementEpisodes?.cancel()
                _etat.update { it.copy(episodes = UiState.Empty(R.string.empty_languages)) }
                return@launch
            }

            selectionnerLangue(langue)
        }
    }

    fun selectionnerLangue(langue: Langue) {
        _etat.update { it.copy(langue = langue) }
        chargerEpisodes(langue)
    }

    private fun chargerEpisodes(langue: Langue) {
        val anime = _etat.value.fiche.dataOrNull ?: return

        chargementEpisodes?.cancel()
        chargementEpisodes = viewModelScope.launch {
            _etat.update { it.copy(episodes = UiState.Loading(R.string.loading_episodes)) }

            runCatchingCancellable { catalogueRepository.episodes(anime, langue) }
                .onSuccess { episodes ->
                    val disponibles = episodes.filter { it.estDisponible }
                    _etat.update {
                        it.copy(
                            episodes = if (disponibles.isEmpty()) {
                                UiState.Empty(R.string.empty_episodes)
                            } else {
                                UiState.Success(disponibles)
                            }
                        )
                    }
                }
                .onFailure { erreur ->
                    _etat.update { it.copy(episodes = UiState.Failure(erreur.toAppError())) }
                }
        }
    }

    companion object {
        fun factory(reference: ReferenceFiche) = viewModelFactoryFor {
            FicheViewModel(
                reference = reference,
                animeRepository = ServiceLocator.animeRepository,
                catalogueRepository = ServiceLocator.catalogueRepository,
                catalogueListeRepository = ServiceLocator.catalogueListeRepository,
                historiqueRepository = ServiceLocator.historiqueRepository
            )
        }
    }
}
