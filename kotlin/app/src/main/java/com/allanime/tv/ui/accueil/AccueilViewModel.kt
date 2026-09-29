package com.allanime.tv.ui.accueil

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.core.dataOrNull
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.historique.HistoriqueRepository
import com.allanime.tv.data.planning.PlanningRepository
import com.allanime.tv.data.repository.CatalogueListeRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.JourSemaine
import com.allanime.tv.domain.model.ProgressionAnime
import com.allanime.tv.domain.model.SortiePlanning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sorties annoncees pour aujourd'hui.
 *
 * @param enChargement `true` tant qu'une verification du planning est en cours *et*
 *   que ce qui est affiche vient d'une copie absente ou perimee. Une copie encore
 *   valable s'affiche immediatement : la verification se fait alors en silence.
 * @param planningConnu `true` des qu'un planning a pu etre lu. Une journee sans
 *   sortie est alors une information a afficher ; un planning jamais obtenu, lui, ne
 *   merite pas de rangee du tout.
 */
data class EtatSortiesDuJour(
    val jour: JourSemaine,
    val sorties: List<SortiePlanning> = emptyList(),
    /**
     * Ce que le planning annoncait les jours precedents, du plus recent au plus
     * ancien. C'est la rangee « Derniers ajouts » : le catalogue ne datant pas ses
     * entrees, le planning est la seule source qui dise quand quelque chose parait.
     */
    val ajoutsRecents: List<SortiePlanning> = emptyList(),
    val enChargement: Boolean = true,
    val planningConnu: Boolean = false
)

/**
 * Etat de l'accueil.
 *
 * L'accueil part desormais du catalogue du site, et non plus des tendances AniList :
 * tout ce qui y figure est reellement lisible dans l'application. Deux
 * telechargements sont lances en parallele a l'ouverture — la liste du catalogue et
 * le planning — et ils sont independants : le planning indisponible laisse l'accueil
 * complet, moins sa rangee de sorties du jour.
 *
 * AniList n'intervient plus qu'a l'ouverture d'une serie, pour enrichir sa fiche.
 */
class AccueilViewModel(
    private val catalogueRepository: CatalogueListeRepository,
    private val planningRepository: PlanningRepository,
    private val historiqueRepository: HistoriqueRepository
) : ViewModel() {

    private val _etat = MutableStateFlow<UiState<List<AnimeCatalogue>>>(
        UiState.Loading(R.string.loading_catalogue)
    )
    val etat: StateFlow<UiState<List<AnimeCatalogue>>> = _etat.asStateFlow()

    /**
     * Rangee « Reprendre votre visionnage ». Se met a jour toute seule : revenir du
     * lecteur reflete immediatement l'episode qu'on vient de regarder.
     */
    val reprises: StateFlow<List<ProgressionAnime>> = historiqueRepository.progressions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** `true` tant que la verification du planning n'a pas rendu sa reponse. */
    private val verificationPlanning = MutableStateFlow(true)

    /**
     * Sorties du jour, lues dans le planning conserve sur le televiseur.
     *
     * La copie locale est affichee sans attendre le reseau ; la verification de fond
     * ne fait rien clignoter tant que le site publie la meme chose, puisque le
     * stockage n'est alors pas reecrit.
     */
    val sortiesDuJour: StateFlow<EtatSortiesDuJour> =
        combine(planningRepository.planning, verificationPlanning) { planning, enCours ->
            val jour = JourSemaine.courant()
            val animes = planning?.animesSeulement()

            EtatSortiesDuJour(
                jour = jour,
                sorties = animes?.sortiesDu(jour).orEmpty(),
                ajoutsRecents = animes
                    ?.sortiesDesJoursPrecedents(jour, PROFONDEUR_AJOUTS)
                    ?.take(MAX_AJOUTS)
                    .orEmpty(),
                enChargement = enCours && (planning == null || planning.estPerime()),
                planningConnu = planning != null
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EtatSortiesDuJour(jour = JourSemaine.courant())
        )

    /**
     * Rangees thematiques : voisinage du dernier anime ouvert, puis le classement du
     * catalogue par type et par genre.
     *
     * Elles sont derivees du catalogue et de l'historique, sans appel reseau
     * supplementaire — les cartes du catalogue portent deja leurs genres. La liste se
     * reconstruit donc toute seule quand l'historique bouge.
     */
    val rangees: StateFlow<List<RangeeAccueil>> =
        combine(_etat, historiqueRepository.progressions) { etat, historique ->
            val catalogue = etat.dataOrNull.orEmpty()
            val vus = historique.mapNotNull { it.slug }.toSet()

            RangeesAccueil.construire(
                catalogue = catalogue,
                reference = referencePourSimilaires(catalogue, historique),
                dejaVus = vus
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        charger()
    }

    /**
     * Entree de catalogue du dernier anime ouvert, pour en tirer les genres.
     *
     * Le rapprochement passe par le slug enregistre dans l'historique : c'est la
     * seule clef commune aux deux sources. Une entree d'historique ecrite avant
     * l'arrivee de ce champ, ou un anime qui a quitte le catalogue, supprime
     * simplement la rangee.
     */
    private fun referencePourSimilaires(
        catalogue: List<AnimeCatalogue>,
        historique: List<ProgressionAnime>
    ): AnimeCatalogue? {
        val slug = historique.maxByOrNull { it.majEpochSeconde }?.slug ?: return null
        return catalogue.firstOrNull { it.slug == slug }
    }

    fun charger(rafraichir: Boolean = false) {
        viewModelScope.launch {
            _etat.value = UiState.Loading(R.string.loading_catalogue)

            runCatchingCancellable { catalogueRepository.animes(rafraichir) }
                .onSuccess { animes ->
                    _etat.value = if (animes.isEmpty()) {
                        UiState.Empty(R.string.empty_catalogue)
                    } else {
                        UiState.Success(animes)
                    }
                }
                .onFailure { _etat.value = UiState.Failure(it.toAppError()) }
        }

        viewModelScope.launch {
            verificationPlanning.value = true

            // Le planning est secondaire : son echec laisse l'accueil intact, avec
            // au pire la copie de la semaine precedente.
            runCatchingCancellable { planningRepository.synchroniser() }
                .onFailure { Log.w(TAG, "Planning non verifie", it) }

            verificationPlanning.value = false
        }
    }

    companion object {
        private const val TAG = "AccueilViewModel"

        /** Nombre de jours remontes par la rangee « Derniers ajouts ». */
        private const val PROFONDEUR_AJOUTS = 3

        /** Au-dela, la rangee devient un annuaire : on la parcourt sans la lire. */
        private const val MAX_AJOUTS = 20

        fun factory() = viewModelFactoryFor {
            AccueilViewModel(
                catalogueRepository = ServiceLocator.catalogueListeRepository,
                planningRepository = ServiceLocator.planningRepository,
                historiqueRepository = ServiceLocator.historiqueRepository
            )
        }
    }
}
