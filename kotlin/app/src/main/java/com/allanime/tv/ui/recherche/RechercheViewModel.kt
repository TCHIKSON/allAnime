package com.allanime.tv.ui.recherche

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.planning.PlanningRepository
import com.allanime.tv.data.repository.AnimeRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.JourSemaine
import com.allanime.tv.domain.model.SortiePlanning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ce que l'ecran de recherche propose **avant** toute saisie.
 *
 * Les deux rangees viennent du planning, seule source qui date ce qu'elle annonce :
 *
 * @param sortiesDuJour ce que le site publie aujourd'hui.
 * @param ajoutsRecents ce qu'il a publie les jours precedents de la semaine, du plus
 *   recent au plus ancien. C'est l'equivalent le plus honnete d'une rubrique
 *   « derniers ajouts » : le catalogue, lui, ne date pas ses entrees, et un
 *   classement invente y ressemblerait sans en etre un.
 */
data class EtatSuggestions(
    val jour: JourSemaine,
    val sortiesDuJour: List<SortiePlanning> = emptyList(),
    val ajoutsRecents: List<SortiePlanning> = emptyList(),
    val enChargement: Boolean = true
) {
    val estVide: Boolean get() = sortiesDuJour.isEmpty() && ajoutsRecents.isEmpty()
}

/**
 * Recherche d'animes (section 7).
 *
 * La recherche interroge AniList, qui classe lui-meme ses resultats par pertinence.
 * Chaque caractere saisi relance donc une requete, temporisee par [DELAI_SAISIE] :
 * sans cela, taper « one piece » en lancerait neuf, dont huit deja perimees a
 * l'arrivee. La requete precedente est annulee des que la saisie change, ce qui evite
 * qu'une reponse tardive recouvre un resultat plus recent.
 *
 * Tant que rien n'est saisi, l'ecran n'est pas vide pour autant : il montre ce que le
 * planning annonce, aujourd'hui puis les jours precedents.
 */
class RechercheViewModel(
    private val repository: AnimeRepository,
    private val planningRepository: PlanningRepository
) : ViewModel() {

    private val _requete = MutableStateFlow("")
    val requete: StateFlow<String> = _requete.asStateFlow()

    private val _etat = MutableStateFlow<UiState<List<Anime>>>(
        UiState.Empty(R.string.empty_search_hint)
    )
    val etat: StateFlow<UiState<List<Anime>>> = _etat.asStateFlow()

    /** Recherche en cours, abandonnee des que la saisie change. */
    private var recherche: Job? = null

    private val verificationPlanning = MutableStateFlow(true)

    val suggestions: StateFlow<EtatSuggestions> =
        combine(planningRepository.planning, verificationPlanning) { planning, enCours ->
            val jour = JourSemaine.courant()
            val animes = planning?.animesSeulement()

            EtatSuggestions(
                jour = jour,
                sortiesDuJour = animes?.sortiesDu(jour).orEmpty(),
                ajoutsRecents = animes
                    ?.sortiesDesJoursPrecedents(jour, PROFONDEUR_AJOUTS)
                    ?.take(MAX_AJOUTS)
                    .orEmpty(),
                enChargement = enCours && planning == null
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EtatSuggestions(jour = JourSemaine.courant())
        )

    init {
        viewModelScope.launch {
            verificationPlanning.value = true
            // Le planning n'est qu'une aide a la saisie : son echec laisse l'ecran
            // parfaitement utilisable, seulement sans ses deux rangees.
            runCatchingCancellable { planningRepository.synchroniser() }
                .onFailure { Log.w(TAG, "Planning non verifie", it) }
            verificationPlanning.value = false
        }
    }

    fun surSaisie(texte: String) {
        _requete.value = texte
        lancer(texte, temporiser = true)
    }

    /** Ajoute un caractere : c'est ce que fait une touche du clavier virtuel. */
    fun ajouter(caractere: String) {
        surSaisie(_requete.value + caractere)
    }

    /** Efface le dernier caractere. Sans effet sur une saisie deja vide. */
    fun effacer() {
        surSaisie(_requete.value.dropLast(1))
    }

    fun toutEffacer() {
        surSaisie("")
    }

    /**
     * Titre dicte a la recherche vocale.
     *
     * Il remplace la saisie, et la recherche part sans la temporisation du clavier :
     * une phrase dictee arrive entiere, il n'y a pas de frappe suivante a attendre.
     */
    fun dicter(texte: String) {
        val titre = texte.trim()
        if (titre.isEmpty()) return

        _requete.value = titre
        lancer(titre, temporiser = false)
    }

    /**
     * Texte reconnu en cours de dictee : il s'affiche dans le champ, sans rien
     * chercher. Une phrase a moitie entendue donnerait des resultats qui sautent.
     */
    fun afficherDictee(texte: String) {
        recherche?.cancel()
        _requete.value = texte
    }

    /** Le bouton « Rechercher » et la touche OK cherchent sans attendre. */
    fun relancer() {
        lancer(_requete.value, temporiser = false)
    }

    private fun lancer(texte: String, temporiser: Boolean) {
        recherche?.cancel()

        val requete = texte.trim()
        if (requete.isBlank()) {
            _etat.value = UiState.Empty(R.string.empty_search_hint)
            return
        }

        recherche = viewModelScope.launch {
            if (temporiser) delay(DELAI_SAISIE)

            _etat.value = UiState.Loading(R.string.loading_search)

            runCatchingCancellable { repository.rechercher(requete) }
                .onSuccess { resultats ->
                    _etat.value = if (resultats.isEmpty()) {
                        UiState.Empty(R.string.empty_search)
                    } else {
                        UiState.Success(resultats)
                    }
                }
                .onFailure { _etat.value = UiState.Failure(it.toAppError()) }
        }
    }

    companion object {
        private const val TAG = "RechercheViewModel"

        /** Temps de frappe laisse a l'utilisateur avant d'interroger AniList. */
        private const val DELAI_SAISIE = 350L

        /** Nombre de jours remontes pour la rangee des ajouts recents. */
        private const val PROFONDEUR_AJOUTS = 3

        /** Au-dela, la rangee devient un annuaire : on la parcourt sans la lire. */
        private const val MAX_AJOUTS = 20

        fun factory() = viewModelFactoryFor {
            RechercheViewModel(
                repository = ServiceLocator.animeRepository,
                planningRepository = ServiceLocator.planningRepository
            )
        }
    }
}
