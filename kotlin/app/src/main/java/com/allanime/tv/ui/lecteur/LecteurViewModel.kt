package com.allanime.tv.ui.lecteur

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.historique.HistoriqueRepository
import com.allanime.tv.data.lecteur.ClassementLecteurs
import com.allanime.tv.data.repository.AnimeRepository
import com.allanime.tv.data.repository.CatalogueRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.LangueCode
import com.allanime.tv.domain.model.Lecteur
import com.allanime.tv.domain.model.OrdreLecteurs
import com.allanime.tv.domain.model.ProgressionAnime
import com.allanime.tv.domain.model.Saison
import com.allanime.tv.domain.model.SelectionLecture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lecture d'un episode (sections 13 et 14).
 *
 * Le ViewModel resout la selection (anime, saison, langue, episode) a partir des
 * caches du catalogue, expose la liste des lecteurs disponibles, gere le passage
 * d'un lecteur a l'autre, l'enchainement des episodes, et tient a jour l'historique
 * de visionnage.
 *
 * Le choix du lecteur est automatique : les hebergeurs sont tentes dans l'ordre de
 * ce qui a deja fonctionne sur ce televiseur, et un lecteur qui n'affiche aucune
 * image est abandonne sans rien demander a l'utilisateur. Le choix manuel reste
 * possible par le menu des reglages, et n'interrompt pas ce filet de securite : si
 * le lecteur choisi echoue a son tour, la bascule reprend la ou elle en etait.
 */
class LecteurViewModel(
    private val animeId: Int,
    private val indexSaison: Int,
    private val codeLangue: String,
    private val numeroEpisodeInitial: Int,
    private val animeRepository: AnimeRepository,
    private val catalogueRepository: CatalogueRepository,
    private val historiqueRepository: HistoriqueRepository,
    private val classementLecteurs: ClassementLecteurs
) : ViewModel() {

    private val _etat = MutableStateFlow<UiState<SelectionLecture>>(
        UiState.Loading(R.string.loading_player)
    )
    val etat: StateFlow<UiState<SelectionLecture>> = _etat.asStateFlow()

    private val _indexLecteur = MutableStateFlow(0)
    val indexLecteur: StateFlow<Int> = _indexLecteur.asStateFlow()

    /**
     * Position a laquelle reprendre la lecture, en secondes.
     *
     * Vaut 0 pour un episode jamais commence. L'ecran l'applique une seule fois, des
     * que la video est detectee.
     */
    private val _positionReprise = MutableStateFlow(0)
    val positionReprise: StateFlow<Int> = _positionReprise.asStateFlow()

    /** Episodes de la saison et de la langue courantes, pour naviguer sans recharger. */
    private var episodes: List<Episode> = emptyList()

    private var anime: Anime? = null

    /** Titre et jaquette de la serie, conserves pour l'historique. */
    private var identite: IdentiteSerie? = null

    /** Derniere seconde enregistree, pour ne pas ecrire a chaque rapport de position. */
    private var derniereSauvegarde = 0

    /**
     * Episodes ouverts au fil de la serie, repris de l'historique puis complete.
     *
     * Il faut le tenir ici : l'entree d'historique est reecrite a chaque sauvegarde,
     * et reconstruire la liste depuis la seule entree precedente la perdrait des le
     * premier changement d'episode.
     */
    private var episodesOuverts: Map<Int, Set<Int>> = emptyMap()

    /**
     * Derniere position rapportee par la video.
     *
     * Elle sert de point de reprise lors d'une bascule de lecteur : quand une source
     * meurt en cours d'episode, on repart ou l'image s'est arretee, et non au debut.
     */
    private var positionCourante = 0

    /** Ordre des tentatives, du lecteur le plus prometteur au moins prometteur. */
    private var ordreTentatives: List<Int> = emptyList()

    /** Lecteurs deja essayes pour l'episode courant, pour ne pas tourner en rond. */
    private val dejaTentes = mutableSetOf<Int>()

    /**
     * Slug du catalogue pour cet anime, resolu au chargement.
     *
     * Il est recopie dans l'historique : c'est lui qui permet a l'accueil de relier
     * une reprise a la liste du catalogue, l'identifiant AniList n'y figurant pas.
     */
    private var slugCatalogue: String? = null

    init {
        charger(numeroEpisodeInitial)
    }

    fun charger(numeroEpisode: Int) {
        viewModelScope.launch {
            _etat.value = UiState.Loading(R.string.loading_player)

            val resultat = runCatchingCancellable { contexte() }
            val contexte = resultat.getOrElse { erreur ->
                _etat.value = UiState.Failure(erreur.toAppError())
                return@launch
            }

            if (contexte == null) {
                // La serie n'est pas dans le catalogue : rien a lire, pas une panne.
                _etat.value = UiState.Empty(R.string.empty_episodes)
                return@launch
            }
            identite = contexte.identite
            slugCatalogue = contexte.identite.slug

            val saison = contexte.saisons.getOrNull(indexSaison)
            if (saison == null) {
                _etat.value = UiState.Empty(R.string.empty_seasons)
                return@launch
            }

            val langue = langueDemandee(saison.langues)
            if (langue == null) {
                _etat.value = UiState.Empty(R.string.empty_languages)
                return@launch
            }

            runCatchingCancellable {
                catalogueRepository.episodes(requireNotNull(anime), langue)
            }
                .onSuccess { liste ->
                    episodes = liste.filter { it.estDisponible }
                    val episode = episodes.firstOrNull { it.numero == numeroEpisode }
                        ?: episodes.firstOrNull()

                    if (episode == null) {
                        _etat.value = UiState.Empty(R.string.empty_episodes)
                        return@onSuccess
                    }

                    preparerTentatives(episode)
                    _etat.value = UiState.Success(
                        SelectionLecture(
                            animeId = animeId,
                            animeTitre = contexte.identite.titre,
                            saison = saison,
                            langue = langue,
                            episode = episode,
                            totalEpisodes = episodes.size
                        )
                    )

                    appliquerRepriseEtHistorique(episode)
                }
                .onFailure { _etat.value = UiState.Failure(it.toAppError()) }
        }
    }

    /** Titre, jaquette, slug eventuel et saisons de la serie. */
    private data class ContexteLecture(
        val identite: IdentiteSerie,
        val saisons: List<Saison>
    )

    /** Identite affichee et conservee dans l'historique. */
    private data class IdentiteSerie(
        val titre: String,
        val image: String?,
        val slug: String?
    )

    /**
     * La fiche AniList donne le titre et la jaquette, le catalogue donne les saisons.
     */
    private suspend fun contexte(): ContexteLecture? {
        val fiche = animeRepository.fiche(animeId) ?: return null
        if (fiche.candidatsCatalogue.isEmpty()) return null
        anime = fiche

        val saisons = catalogueRepository.saisons(fiche)
        // Deja resolu et memorise par l'appel ci-dessus : aucune requete de plus.
        val slug = runCatchingCancellable { catalogueRepository.slug(fiche) }.getOrNull()

        return ContexteLecture(
            identite = IdentiteSerie(fiche.displayTitle, fiche.bestCover, slug),
            saisons = saisons
        )
    }

    /**
     * Cherche une progression connue pour cet episode precis et, a defaut, ouvre une
     * nouvelle entree d'historique des l'ouverture du lecteur.
     */
    private suspend fun appliquerRepriseEtHistorique(episode: Episode) {
        val precedente = historiqueRepository.progressionPour(animeId)

        // L'episode qu'on vient d'ouvrir rejoint la liste : c'est ce qui permettra a
        // la fiche de ne deflouter que ce qui a reellement ete regarde.
        episodesOuverts = (precedente?.episodesOuverts ?: emptyMap()).let { connus ->
            connus + (indexSaison to (connus[indexSaison].orEmpty() + episode.numero))
        }

        val reprise = precedente
            ?.takeIf {
                it.indexSaison == indexSaison &&
                    it.numeroEpisode == episode.numero &&
                    !it.estTermine
            }
            ?.positionSecondes
            ?: 0

        _positionReprise.value = reprise
        derniereSauvegarde = reprise
        sauvegarder(reprise, precedente?.dureeSecondes ?: 0)
    }

    /** Lecteur actuellement charge, ou `null` si l'episode n'en propose aucun. */
    val lecteurCourant: Lecteur?
        get() = (_etat.value as? UiState.Success)?.data?.episode?.lecteurs
            ?.getOrNull(_indexLecteur.value)

    /**
     * Prepare l'ordre des tentatives pour un episode et charge le lecteur le plus
     * prometteur. L'ordre vient du comportement deja observe sur ce televiseur.
     */
    private suspend fun preparerTentatives(episode: Episode) {
        val scores = runCatchingCancellable { classementLecteurs.scores() }.getOrDefault(emptyMap())

        ordreTentatives = OrdreLecteurs.ordonner(episode.lecteurs, scores)
        dejaTentes.clear()

        val premier = ordreTentatives.firstOrNull() ?: 0
        dejaTentes += premier
        _indexLecteur.value = premier
    }

    /** Choix explicite de l'utilisateur, depuis le menu des reglages. */
    fun changerLecteur(index: Int) {
        val lecteurs = (_etat.value as? UiState.Success)?.data?.episode?.lecteurs ?: return
        if (index !in lecteurs.indices) return

        dejaTentes += index
        // Un changement volontaire repart de l'endroit ou l'on en etait.
        _positionReprise.value = positionCourante
        _indexLecteur.value = index
    }

    /**
     * Le lecteur courant n'a rien affiche : on note l'echec de son hebergeur et on
     * enchaine sur le premier lecteur encore jamais essaye pour cet episode.
     *
     * @return `false` quand tous les lecteurs ont ete essayes. L'interface propose
     *   alors la reprise manuelle plutot que de boucler indefiniment.
     */
    fun echecLecteurCourant(): Boolean {
        val hote = lecteurCourant?.hostLabel
        if (!hote.isNullOrBlank()) {
            viewModelScope.launch { classementLecteurs.signalerEchec(hote) }
        }

        return avancerLecteur()
    }

    /**
     * Passe au premier lecteur encore jamais essaye pour cet episode, sans toucher a
     * l'adresse : c'est l'appelant qui decide quand la resoudre.
     *
     * @return `false` quand tous ont ete essayes.
     */
    private fun avancerLecteur(): Boolean {
        val suivant = ordreTentatives.firstOrNull { it !in dejaTentes } ?: return false

        dejaTentes += suivant
        // Une source qui meurt en cours de route ne doit pas faire perdre l'avancement.
        _positionReprise.value = positionCourante
        _indexLecteur.value = suivant
        return true
    }

    /** Des images defilent : l'hebergeur courant devient prioritaire pour la suite. */
    fun succesLecteurCourant() {
        val hote = lecteurCourant?.hostLabel?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { classementLecteurs.signalerSucces(hote) }
    }

    fun episodeSuivant(): Boolean = allerVersEpisode(decalage = 1)

    fun episodePrecedent(): Boolean = allerVersEpisode(decalage = -1)

    /**
     * Rapport de position emis par le lecteur.
     *
     * L'ecriture sur disque est espacee : inutile d'ecrire toutes les deux secondes
     * pour une barre de progression.
     */
    fun surProgression(positionSecondes: Int, dureeSecondes: Int) {
        if (positionSecondes <= 0) return
        positionCourante = positionSecondes

        val ecartSuffisant = kotlin.math.abs(positionSecondes - derniereSauvegarde) >= PAS_SAUVEGARDE
        if (!ecartSuffisant) return

        derniereSauvegarde = positionSecondes
        viewModelScope.launch { sauvegarder(positionSecondes, dureeSecondes) }
    }

    private suspend fun sauvegarder(positionSecondes: Int, dureeSecondes: Int) {
        val selection = (_etat.value as? UiState.Success)?.data ?: return
        val serie = identite ?: return

        historiqueRepository.enregistrer(
            ProgressionAnime(
                animeId = animeId,
                titre = serie.titre,
                slug = slugCatalogue,
                episodesOuverts = episodesOuverts,
                image = serie.image,
                indexSaison = indexSaison,
                nomSaison = selection.saison.nom,
                codeLangue = selection.langue.code.code,
                numeroEpisode = selection.episode.numero,
                totalEpisodes = selection.totalEpisodes,
                positionSecondes = positionSecondes,
                dureeSecondes = dureeSecondes,
                majEpochSeconde = System.currentTimeMillis() / 1000
            )
        )
    }

    private fun allerVersEpisode(decalage: Int): Boolean {
        val selection = (_etat.value as? UiState.Success)?.data ?: return false
        val position = episodes.indexOfFirst { it.numero == selection.episode.numero }
        if (position < 0) return false

        val cible = episodes.getOrNull(position + decalage) ?: return false

        _positionReprise.value = 0
        derniereSauvegarde = 0
        positionCourante = 0
        _etat.value = UiState.Success(selection.copy(episode = cible))

        viewModelScope.launch {
            preparerTentatives(cible)
            episodesOuverts = episodesOuverts.let { connus ->
                connus + (indexSaison to (connus[indexSaison].orEmpty() + cible.numero))
            }
            sauvegarder(positionSecondes = 0, dureeSecondes = 0)
        }
        return true
    }

    private fun langueDemandee(langues: List<Langue>): Langue? {
        val code = LangueCode.fromCode(codeLangue)
        return langues.firstOrNull { it.code == code }
            ?: langues.firstOrNull { it.code == LangueCode.VOSTFR }
            ?: langues.firstOrNull()
    }

    companion object {
        /** Ecart minimal, en secondes, entre deux ecritures de l'historique. */
        private const val PAS_SAUVEGARDE = 10

        fun factory(
            animeId: Int,
            indexSaison: Int,
            codeLangue: String,
            numeroEpisode: Int
        ) = viewModelFactoryFor {
            LecteurViewModel(
                animeId = animeId,
                indexSaison = indexSaison,
                codeLangue = codeLangue,
                numeroEpisodeInitial = numeroEpisode,
                animeRepository = ServiceLocator.animeRepository,
                catalogueRepository = ServiceLocator.catalogueRepository,
                historiqueRepository = ServiceLocator.historiqueRepository,
                classementLecteurs = ServiceLocator.classementLecteurs
            )
        }
    }
}
