package com.allanime.tv.ui.fiche

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.core.Genres
import com.allanime.tv.core.LibellesAniList
import com.allanime.tv.core.UiState
import com.allanime.tv.core.dataOrNull
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.LangueCode
import com.allanime.tv.domain.model.Saison
import com.allanime.tv.domain.model.StreamingEpisode
import com.allanime.tv.ui.components.BlocChargement
import com.allanime.tv.ui.components.BlocMessage
import com.allanime.tv.ui.components.ZoneDefilante
import com.allanime.tv.ui.components.BoutonTv
import com.allanime.tv.ui.components.CarteEpisodePaysage
import com.allanime.tv.ui.components.CartePersonnageVoix
import com.allanime.tv.ui.components.CarteStaff
import com.allanime.tv.ui.components.ChampInformation
import com.allanime.tv.ui.components.ContenuSelonEtat
import com.allanime.tv.ui.components.ImageDistante
import com.allanime.tv.ui.components.Puce
import com.allanime.tv.ui.components.PuceSelection
import com.allanime.tv.ui.components.VignetteLegendee
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Gabarit
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.InkSurface
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Page d'une serie : banniere, bouton de lecture, puis la liste des episodes et les
 * sections d'informations, toutes dans la meme page defilante.
 *
 * Le choix de l'episode fait partie de cette page : il n'y a pas d'ecran separe, ce
 * qui evite un aller-retour supplementaire a la telecommande. Les episodes sont
 * disposes en grille, chaque ligne etant un element de la liste paresseuse : une
 * saison de mille episodes ne compose donc que ce qui est a l'ecran.
 *
 * L'ordre de la page suit l'ordre des intentions : regarder d'abord — banniere,
 * bouton de reprise, saison, langue, episodes — puis se renseigner : synopsis,
 * tableau d'informations, genres, distribution et recommandations.
 */
@Composable
fun FicheScreen(
    reference: ReferenceFiche,
    onLireEpisode: (
        animeId: Int,
        indexSaison: Int,
        codeLangue: String,
        numeroEpisode: Int
    ) -> Unit,
    onOuvrirFiche: (Int) -> Unit,
    onLireBandeAnnonce: (url: String, titre: String) -> Unit,
    viewModel: FicheViewModel = viewModel(
        key = "fiche-${reference.cle}",
        factory = FicheViewModel.factory(reference)
    )
) {
    // Lu ici, et non dans les sections : celles-ci sont des extensions de
    // `LazyListScope`, qui s'executent a la construction de la liste et non en
    // composition — `LocalGabarit` n'y est pas accessible. Il leur est donc passe.
    val gabarit = LocalGabarit.current
    val etat by viewModel.etat.collectAsState()
    val boutonLire = remember { FocusRequester() }

    // Le bouton principal n'est focalisable qu'une fois un episode connu, ce qui
    // demande deux allers-retours reseau. Sans cette seconde tentative, la page
    // s'ouvrait sans focus et la telecommande n'avait aucune prise. Le drapeau
    // garantit qu'on ne reprend jamais le focus a l'utilisateur ensuite.
    var focusPose by remember(reference.cle) { mutableStateOf(false) }
    val pretALire = etat.episodeALire != null

    LaunchedEffect(reference.cle, pretALire) {
        if (!focusPose) runCatching { boutonLire.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(InkBackground)) {
        ContenuSelonEtat(
            etat = etat.fiche,
            modifier = Modifier.fillMaxSize(),
            surReessayer = viewModel::charger
        ) { anime ->
            val saisons = etat.saisons.dataOrNull
            val episodesCharges = etat.episodes.dataOrNull

            val vignettes = remember(anime.id, saisons?.size, episodesCharges?.size) {
                // Une fiche AniList ne couvre qu'une saison, alors que le catalogue
                // en regroupe plusieurs sous le meme identifiant : les vignettes ne
                // sont utilisables que lorsque la correspondance est certaine.
                val diffuses = if (saisons?.size == 1) anime.episodesDiffuses else emptyMap()
                val total = episodesCharges?.size ?: 0

                // AniList n'illustre parfois que les deux premiers episodes. Une
                // grille a moitie illustree se lit plus mal qu'une grille
                // uniformement numerotee : en dessous du seuil, on s'en passe.
                if (total > 0 && diffuses.size >= total * COUVERTURE_VIGNETTES) {
                    diffuses
                } else {
                    emptyMap()
                }
            }

            // La plage affichee se remet a zero des qu'on change de saison ou de langue.
            var indexPlage by remember(etat.indexSaison, etat.langue?.code) {
                mutableIntStateOf(0)
            }

            // Un onglet sans contenu ne s'affiche pas : une fiche qu'AniList ignore
            // n'a ni distribution ni suggestion, sa barre se reduit a deux entrees.
            val onglets = remember(anime.id) {
                buildList {
                    add(OngletFiche.EPISODES)
                    if (anime.recommendations.isNotEmpty()) add(OngletFiche.SUGGESTIONS)
                    if (anime.characters.isNotEmpty()) add(OngletFiche.PERSONNAGES)
                    if (anime.staff.isNotEmpty()) add(OngletFiche.STAFF)
                    add(OngletFiche.DETAILS)
                }
            }
            var onglet by remember(anime.id) { mutableStateOf(OngletFiche.EPISODES) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = gabarit.margeFin),
                verticalArrangement =
                    Arrangement.spacedBy((ESPACEMENT_PAGE * gabarit.facteur).dp)
            ) {
                item(key = "entete") {
                    Entete(
                        anime = anime,
                        etat = etat,
                        boutonLire = boutonLire,
                        surFocusLire = { focusPose = true },
                        onLire = {
                            val langue = etat.langue ?: return@Entete
                            val episode = etat.episodeALire ?: return@Entete
                            onLireEpisode(
                                anime.id,
                                etat.indexSaison,
                                langue.code.code,
                                episode
                            )
                        },
                        onLireBandeAnnonce = { url ->
                            onLireBandeAnnonce(url, anime.displayTitle)
                        }
                    )
                }

                item(key = "onglets") {
                    BarreOnglets(
                        onglets = onglets,
                        courant = onglet,
                        onChoisir = { onglet = it }
                    )
                }

                when (onglet) {
                    OngletFiche.EPISODES -> sectionEpisodes(
                        anime = anime,
                        gabarit = gabarit,
                        etat = etat,
                        vignettes = vignettes,
                        indexPlage = indexPlage,
                        onPlage = { indexPlage = it },
                        onSaison = viewModel::selectionnerSaison,
                        onLangue = viewModel::selectionnerLangue,
                        onReessayer = viewModel::rechargerSaisons,
                        onLireEpisode = { numero ->
                            val langue = etat.langue ?: return@sectionEpisodes
                            onLireEpisode(anime.id, etat.indexSaison, langue.code.code, numero)
                        }
                    )

                    OngletFiche.SUGGESTIONS ->
                        sectionSuggestions(anime, gabarit, onOuvrirFiche)

                    OngletFiche.PERSONNAGES -> sectionPersonnages(anime, gabarit)

                    OngletFiche.STAFF -> sectionStaff(anime, gabarit)

                    OngletFiche.DETAILS -> sectionDetails(
                        anime = anime,
                        gabarit = gabarit,
                        catalogue = etat.catalogue,
                        langues = etat.languesDisponibles
                    )
                }
            }
        }
    }
}

/**
 * Banniere, jaquette, identite de la serie et boutons d'action.
 *
 * La hauteur est fixe : la banniere et la jaquette s'y ajustent, et le synopsis y
 * est volontairement coupe a deux lignes — il est repris en entier plus bas. Une
 * entete qui grandirait avec le texte ferait sauter la mise en page d'une fiche a
 * l'autre.
 */
@Composable
private fun Entete(
    anime: Anime,
    etat: EtatFiche,
    boutonLire: FocusRequester,
    surFocusLire: () -> Unit,
    onLire: () -> Unit,
    onLireBandeAnnonce: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Box(modifier = Modifier.fillMaxWidth().height(gabarit.hauteurEnteteFiche)) {
        ImageDistante(
            url = anime.bannerImage ?: anime.bestCover,
            contentDescription = anime.displayTitle,
            modifier = Modifier.fillMaxSize(),
            coins = 0.dp
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        InkBackground,
                        InkBackground.copy(alpha = 0.9f),
                        InkBackground.copy(alpha = 0.2f)
                    )
                )
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(InkBackground.copy(alpha = 0f), InkBackground))
                )
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            ImageDistante(
                url = anime.bestCover,
                contentDescription = stringResource(R.string.cd_cover, anime.displayTitle),
                modifier = Modifier.fillMaxHeight().aspectRatio(RATIO_JAQUETTE)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.displayTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = PureWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                anime.title.romaji
                    ?.takeIf { it != anime.displayTitle }
                    ?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            color = MistGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                // Quatre valeurs au plus, la note en tete et en corail. Cette
                // rangee ne peut pas defiler — aucune de ses etiquettes ne prend le
                // focus — donc tout ce qui deborderait serait perdu : le reste est
                // dans le tableau d'informations, plus bas, ou il se lit posement.
                RangeeMeta(
                    puces = listOfNotNull(
                        anime.averageScore?.let {
                            InfoPuce(stringResource(R.string.label_score, it), accentuee = true)
                        },
                        LibellesAniList.format(anime.format)?.let { InfoPuce(it) },
                        LibellesAniList.statut(anime.status)?.let { InfoPuce(it) },
                        LibellesAniList.saisonEtAnnee(anime.season, anime.seasonYear)
                            ?.let { InfoPuce(it) }
                    ),
                    modifier = Modifier.padding(top = 10.dp)
                )

                anime.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistGrey,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val episode = etat.episodeALire
                    BoutonTv(
                        texte = if (etat.progression != null && episode != null) {
                            stringResource(R.string.action_resume_episode, episode)
                        } else {
                            stringResource(R.string.action_play)
                        },
                        onClick = onLire,
                        actif = episode != null,
                        modifier = Modifier
                            .focusRequester(boutonLire)
                            .onFocusChanged { if (it.isFocused) surFocusLire() }
                    )

                    // La bande-annonce se lit dans le lecteur de l'application :
                    // aucun navigateur n'est ouvert, le bouton Retour ramene ici.
                    anime.trailer?.embedUrl?.let { url ->
                        BoutonTv(
                            texte = stringResource(R.string.action_watch_trailer),
                            onClick = { onLireBandeAnnonce(url) },
                            principal = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Onglet « Episodes ».
 *
 * Trois etages, de haut en bas : le choix de la saison, celui de la langue et de la
 * plage, puis la liste elle-meme, qui defile **horizontalement**.
 *
 * Le defilement horizontal n'est pas un choix esthetique. Sur un televiseur, une
 * liste verticale d'episodes impose de descendre episode par episode ; une rangee se
 * parcourt a la fleche droite maintenue, et laisse au-dessus d'elle la place
 * d'afficher les saisons en permanence — on voit donc toujours ou l'on est.
 *
 * Les vignettes sont au format paysage parce que c'est celui des captures d'ecran
 * que les sources fournissent : les presenter en portrait obligerait a les rogner,
 * c'est-a-dire a jeter la moitie de l'image.
 */
private fun LazyListScope.sectionEpisodes(
    anime: Anime,
    gabarit: Gabarit,
    etat: EtatFiche,
    vignettes: Map<Int, StreamingEpisode>,
    indexPlage: Int,
    onPlage: (Int) -> Unit,
    onSaison: (Int) -> Unit,
    onLangue: (Langue) -> Unit,
    onReessayer: () -> Unit,
    onLireEpisode: (Int) -> Unit
) {
    when (val saisons = etat.saisons) {
        is UiState.Loading -> item(key = "saisons-chargement") {
            BlocEtat { BlocChargement(saisons.messageRes) }
        }

        is UiState.Empty -> item(key = "saisons-vide") {
            BlocEtat { BlocMessage(message = stringResource(saisons.messageRes)) }
        }

        is UiState.Failure -> item(key = "saisons-echec") {
            BlocEtat(hauteur = gabarit.hauteurEnteteFiche) {
                BlocMessage(
                    message = stringResource(saisons.error.messageRes),
                    libelleAction = stringResource(R.string.action_retry),
                    action = onReessayer
                )
            }
        }

        is UiState.Success -> {
            // Les saisons ne sont montrees que s'il y a un choix a faire : une serie
            // en saison unique n'a pas besoin d'un selecteur a une entree.
            if (saisons.data.size > 1) {
                item(key = "saisons") {
                    OngletsSaisons(
                        saisons = saisons.data,
                        indexCourant = etat.indexSaison,
                        onSaison = onSaison
                    )
                }
            }

            item(key = "choix-episodes") {
                BarreChoix(
                    etat = etat,
                    indexPlage = indexPlage,
                    onPlage = onPlage,
                    onLangue = onLangue
                )
            }

            item(key = "episodes") {
                RangeeEpisodes(
                    anime = anime,
                    etat = etat,
                    vignettes = vignettes,
                    indexPlage = indexPlage,
                    onLireEpisode = onLireEpisode
                )
            }
        }
    }
}

/**
 * Selecteur de saison, en onglets horizontaux.
 *
 * La saison courante est marquee en corail, comme partout ailleurs dans
 * l'application : le focus, lui, reste blanc. Les deux ne doivent pas se confondre.
 */
/**
 * Rappel de ce que les selecteurs viennent de decider : saison, langue et nombre
 * d'episodes disponibles. C'est aussi la seule indication de langue quand la serie
 * n'en propose qu'une — auquel cas aucune pastille ne s'affiche.
 */
@Composable
private fun EnteteEpisodes(etat: EtatFiche) {
    val plusieursSaisons = (etat.saisons.dataOrNull?.size ?: 0) > 1
    val disponibles = etat.episodes.dataOrNull?.size

    val resume = listOfNotNull(
        etat.saisonCourante?.nom?.takeIf { plusieursSaisons },
        etat.langue?.code?.label,
        disponibles?.let { nombre ->
            if (nombre == 1) {
                stringResource(R.string.label_one_episode_available)
            } else {
                stringResource(R.string.label_episodes_available, nombre)
            }
        }
    ).joinToString(SEPARATEUR)

    if (resume.isBlank()) return

    Text(
        text = resume,
        style = MaterialTheme.typography.titleSmall,
        color = MistGrey,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OngletsSaisons(
    saisons: List<Saison>,
    indexCourant: Int,
    onSaison: (Int) -> Unit
) {
    val gabarit = LocalGabarit.current

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            horizontal = gabarit.margeFin,
            vertical = (6 * gabarit.facteur).dp
        )
    ) {
        itemsIndexed(saisons, key = { _, saison -> saison.path }) { index, saison ->
            PuceSelection(
                texte = saison.nom,
                selectionnee = index == indexCourant,
                onClick = { onSaison(index) }
            )
        }
    }
}

/**
 * Langue, plage d'episodes et rappel de la selection, sur une seule ligne.
 *
 * Les deux selecteurs n'apparaissent que lorsqu'il y a reellement un choix : une
 * serie en version originale seule, ou une saison de douze episodes, n'ont pas a
 * porter des pastilles qui ne mènent nulle part.
 */
@Composable
private fun BarreChoix(
    etat: EtatFiche,
    indexPlage: Int,
    onPlage: (Int) -> Unit,
    onLangue: (Langue) -> Unit
) {
    val gabarit = LocalGabarit.current

    val langues = etat.saisonCourante?.langues.orEmpty()
    val episodes = etat.episodes.dataOrNull.orEmpty()
    val plages = episodes.chunked(TAILLE_PLAGE)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = gabarit.margeFin),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EnteteEpisodes(etat)

        if (langues.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(langues, key = { it.path }) { langue ->
                    PuceSelection(
                        texte = langue.code.label,
                        selectionnee = langue.code == etat.langue?.code,
                        onClick = { onLangue(langue) }
                    )
                }
            }
        }

        // Au-dela d'une plage, on offre des raccourcis : atteindre l'episode 300 a la
        // fleche n'est pas une interface.
        if (plages.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(plages, key = { _, lot -> lot.first().numero }) { index, lot ->
                    PuceSelection(
                        texte = stringResource(
                            R.string.label_episode_range,
                            lot.first().numero,
                            lot.last().numero
                        ),
                        selectionnee = index == indexPlage,
                        onClick = { onPlage(index) }
                    )
                }
            }
        }
    }
}

/** La rangee des vignettes, ou l'etat de la liste quand il n'y a rien a montrer. */
@Composable
private fun RangeeEpisodes(
    anime: Anime,
    etat: EtatFiche,
    vignettes: Map<Int, StreamingEpisode>,
    indexPlage: Int,
    onLireEpisode: (Int) -> Unit
) {
    val gabarit = LocalGabarit.current

    when (val episodes = etat.episodes) {
        is UiState.Loading -> BlocEtat(hauteur = gabarit.hauteurRangeeEpisodes) {
            BlocChargement(episodes.messageRes)
        }

        is UiState.Empty -> BlocEtat(hauteur = gabarit.hauteurRangeeEpisodes) {
            BlocMessage(message = stringResource(episodes.messageRes))
        }

        is UiState.Failure -> BlocEtat(hauteur = gabarit.hauteurRangeeEpisodes) {
            BlocMessage(message = stringResource(episodes.error.messageRes))
        }

        is UiState.Success -> {
            val plages = episodes.data.chunked(TAILLE_PLAGE)
            val plage = plages.getOrNull(indexPlage) ?: plages.firstOrNull().orEmpty()

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    horizontal = gabarit.margeFin,
                    vertical = (12 * gabarit.facteur).dp
                )
            ) {
                items(plage, key = { episode -> episode.numero }) { episode ->
                    VignetteEpisode(
                        episode = episode,
                        gabarit = gabarit,
                        anime = anime,
                        etat = etat,
                        vignette = vignettes[episode.numero],
                        onLireEpisode = onLireEpisode
                    )
                }
            }
        }
    }
}

/**
 * Une vignette de la rangee.
 *
 * Deux informations y sont deduites de l'historique, et d'aucune autre source :
 *
 *  - **l'avancement**, qui n'existe que pour l'episode reellement commence dans
 *    cette saison. C'est lui qui commande aussi le flou de la vignette ;
 *  - **la pastille**. Le temps restant quand l'episode est en cours ; « Nouveau »
 *    pour les episodes qui viennent **apres** le dernier regarde — c'est le seul sens
 *    verifiable qu'on puisse donner a ce mot, aucune source ne datant les episodes un
 *    par un.
 */
@Composable
private fun VignetteEpisode(
    episode: Episode,
    gabarit: Gabarit,
    anime: Anime,
    etat: EtatFiche,
    vignette: StreamingEpisode?,
    onLireEpisode: (Int) -> Unit
) {
    val progression = etat.progression?.takeIf { it.indexSaison == etat.indexSaison }
    val enCours = progression?.numeroEpisode == episode.numero

    // Seul un episode **reellement ouvert** est deflloute, et lui seul : l'historique
    // retient desormais la liste de ceux qu'on a lances, saison par saison. Le rang
    // atteint ne suffisait pas — sauter a l'episode 10 defloutait les neuf
    // precedents, qu'on n'avait pourtant jamais vus.
    val ouvert = etat.progression?.aEteOuvert(etat.indexSaison, episode.numero) == true

    // L'avancement, lui, n'est connu que pour l'episode courant : c'est le seul dont
    // l'historique porte la position.
    val avancement = progression
        ?.takeIf { enCours }
        ?.avancement
        ?.takeIf { it > 0f }

    val restant = progression
        ?.takeIf { enCours && !it.estTermine && it.dureeSecondes > 0 }
        ?.let { (it.dureeSecondes - it.positionSecondes) / SECONDES_PAR_MINUTE }
        ?.takeIf { it > 0 }

    val pastille = when {
        restant != null -> stringResource(R.string.label_episode_remaining, restant)
        progression != null && episode.numero > progression.numeroEpisode ->
            stringResource(R.string.label_episode_new)

        else -> null
    }

    CarteEpisodePaysage(
        numero = stringResource(R.string.label_episode, episode.numero),
        titre = vignette?.titreCourt,
        // Les lecteurs ne sont lus qu'au lancement de l'episode : tant qu'ils ne le
        // sont pas, mieux vaut ne rien annoncer qu'afficher « 0 lecteur ».
        detail = episode.lecteurs.size.takeIf { it > 0 }?.let { nombre ->
            pluralStringResource(R.plurals.label_players_count, nombre, nombre)
        }.orEmpty(),
        // A defaut de capture, la jaquette de la serie : aucune carte ne doit etre un
        // rectangle gris.
        imageUrl = vignette?.thumbnail ?: anime.bestCover,
        avancement = avancement,
        flou = !ouvert,
        pastille = pastille,
        enCours = enCours,
        largeur = gabarit.largeurVignetteEpisode,
        onClick = { onLireEpisode(episode.numero) }
    )
}

/**
 * Encadrement commun des etats de chargement, vides et en echec.
 *
 * La hauteur laisse la place a l'illustration de `BlocMessage` : une saison sans
 * episode disponible affiche la meme chose qu'un catalogue vide ailleurs dans
 * l'application, plutot qu'une ligne de texte perdue au milieu de la page.
 */
@Composable
private fun BlocEtat(
    hauteur: Dp = LocalGabarit.current.hauteurRangeeEpisodes,
    contenu: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(hauteur)) { contenu() }
}

/**
 * Onglet « Suggestions » : les recommandations d'AniList, en grille.
 *
 * Une grille plutot qu'une rangee qui defile : l'onglet n'a que cela a montrer,
 * autant que tout soit atteignable en descendant, sans longer une rangee a la
 * fleche droite.
 */
private fun LazyListScope.sectionSuggestions(
    anime: Anime,
    gabarit: Gabarit,
    onOuvrirFiche: (Int) -> Unit
) {
    val largeur = (LARGEUR_SUGGESTION * gabarit.facteur).dp
    grille(anime.recommendations, gabarit.colonnesFiche(largeur), "suggestion") { reco ->
        VignetteLegendee(
            titre = reco.displayTitle,
            sousTitre = null,
            imageUrl = reco.coverImage,
            onClick = { reco.mediaId?.let(onOuvrirFiche) },
            largeur = largeur
        )
    }
}

/** Onglet « Personnages et voix » : une carte par personnage, le doubleur en face. */
private fun LazyListScope.sectionPersonnages(anime: Anime, gabarit: Gabarit) {
    val largeur = (LARGEUR_PERSONNAGE * gabarit.facteur).dp
    grille(anime.characters, gabarit.colonnesFiche(largeur), "personnage") { role ->
        CartePersonnageVoix(
            role = role,
            roleTraduit = roleTraduit(role.role),
            onClick = {},
            largeur = largeur
        )
    }
}

/** Onglet « Staff » : realisation, composition, animation. */
private fun LazyListScope.sectionStaff(anime: Anime, gabarit: Gabarit) {
    val membres = anime.staff.distinctBy { it.member.id ?: it.member.fullName }
    val largeur = (LARGEUR_STAFF * gabarit.facteur).dp

    grille(membres, gabarit.colonnesFiche(largeur), "staff") { staff ->
        CarteStaff(
            staff = staff,
            roleAffiche = RolesStaff.traduire(staff.role),
            onClick = {},
            largeur = largeur
        )
    }
}

/**
 * Grille generique d'un onglet : une ligne de la liste paresseuse par rangee, les
 * places manquantes de la derniere ligne restant vides pour garder l'alignement.
 */
private fun <T> LazyListScope.grille(
    elements: List<T>,
    colonnes: Int,
    prefixeCle: String,
    carte: @Composable (T) -> Unit
) {
    val lignes = elements.chunked(colonnes)

    itemsIndexed(lignes, key = { index, _ -> "$prefixeCle-$index" }) { _, ligne ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ligne.forEach { element -> carte(element) }
        }
    }
}

/**
 * Onglet « Details », en deux colonnes.
 *
 * A gauche le recit — titre, synopsis, avertissement — a droite les metadonnees, en
 * paires libelle / valeur empilees. Les deux se lisent independamment : on parcourt
 * la colonne de droite du regard sans avoir a traverser le synopsis.
 */
private fun LazyListScope.sectionDetails(
    anime: Anime,
    gabarit: Gabarit,
    catalogue: AnimeCatalogue?,
    langues: List<LangueCode>
) {
    item(key = "details") {
        // Les deux colonnes sont posees dans une zone parcourable : un synopsis
        // long, une distribution fournie ou une liste de genres etendue depassent
        // la hauteur disponible, et sans cela la fin resterait inatteignable — rien
        // ici n'etant focalisable, la page n'aurait aucune raison de defiler.
        ZoneDefilante(
            modifier = Modifier
                .fillMaxWidth()
                .height(gabarit.hauteurDetails)
                .padding(horizontal = gabarit.margeFin)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                ColonneRecit(anime = anime, modifier = Modifier.weight(1.4f))
                ColonneMetadonnees(
                    anime = anime,
                    catalogue = catalogue,
                    langues = langues,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Colonne de gauche : ce qui se lit en continu. */
@Composable
private fun ColonneRecit(anime: Anime, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = PureWhite
        )

        anime.title.romaji
            ?.takeIf { it != anime.displayTitle }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MistGrey
                )
            }

        anime.description?.let { synopsis ->
            Text(
                text = synopsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MistGrey
            )
        }

        Avertissement()
    }
}

/**
 * Avertissement de photosensibilite.
 *
 * Il est general et non propre a une serie : ni AniList ni le catalogue ne signalent
 * les sequences clignotantes, et l'animation en comporte assez souvent pour que
 * taire l'avertissement soit le plus mauvais des deux choix. Le texte le dit donc au
 * conditionnel, sans pretendre a une classification qu'aucune source ne fournit.
 */
@Composable
private fun Avertissement() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InkSurface)
            .height(IntrinsicSize.Min)
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Coral))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.warning_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )
            Text(
                text = stringResource(R.string.warning_photosensitivity),
                style = MaterialTheme.typography.bodySmall,
                color = MistGrey
            )
        }
    }
}

/**
 * Colonne de droite : les metadonnees, en paires libelle / valeur.
 *
 * Un champ absent ne laisse pas de trou : la colonne se resserre. Rien n'est
 * invente — pas de classification par age la ou aucune source n'en fournit, pas de
 * definition video la ou elle depend du lecteur choisi.
 */
@Composable
private fun ColonneMetadonnees(
    anime: Anime,
    catalogue: AnimeCatalogue?,
    langues: List<LangueCode>,
    modifier: Modifier = Modifier
) {
    // L'accueil ne classe une serie que sous un seul genre ; sa fiche les montre
    // tous. Le catalogue connait « Ghibli » ou « Isekai », qu'AniList ignore.
    val genres = (anime.genres.map { Genres.traduire(it) } + catalogue?.genres.orEmpty())
        .distinct()

    val champs = listOfNotNull(
        periodeDeDiffusion(anime)?.let { stringResource(R.string.info_release_date) to it },
        genres.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.info_genre) to it.joinToString(", ") },
        stringResource(R.string.rating_adult).takeIf { anime.isAdult }
            ?.let { stringResource(R.string.info_rating) to it },
        createurs(anime)?.let { stringResource(R.string.info_creator) to it },
        realisateurs(anime)?.let { stringResource(R.string.info_director) to it },
        anime.mainStudio?.name?.let { stringResource(R.string.info_studio) to it },
        LibellesAniList.statut(anime.status)
            ?.let { stringResource(R.string.info_status) to it },
        anime.episodes?.let { stringResource(R.string.info_episodes) to it.toString() },
        anime.duration
            ?.let { stringResource(R.string.info_duration) to stringResource(R.string.label_duration, it) },
        LibellesAniList.saisonEtAnnee(anime.season, anime.seasonYear)
            ?.let { stringResource(R.string.info_season) to it },
        LibellesAniList.source(anime.source)
            ?.let { stringResource(R.string.info_source) to it },
        anime.averageScore
            ?.let { stringResource(R.string.info_score) to stringResource(R.string.label_score, it) },
        LibellesAniList.pays(anime.countryOfOrigin)
            ?.let { stringResource(R.string.info_origin) to it },
        anime.tags.filterNot { it.isMediaSpoiler }.take(8).map { it.name }
            .takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.info_themes) to it.joinToString(", ") },
        anime.externalLinks.mapNotNull { it.site }.filter { it.isNotBlank() }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.section_external_links) to it.joinToString(", ") }
    )

    // Format et langues sont des etiquettes et non des phrases : ce sont les seules
    // valeurs qu'on cherche du regard plutot qu'on ne lit.
    val badges = listOfNotNull(LibellesAniList.format(anime.format)) + langues.map { it.label }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (badges.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.info_formats),
                    style = MaterialTheme.typography.labelMedium,
                    color = MistGrey
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.take(4).forEach { badge -> Puce(texte = badge) }
                }
            }
        }

        champs.forEach { (libelle, valeur) ->
            ChampInformation(libelle = libelle, valeur = valeur)
        }
    }
}

/** `2004 – 2011`, `2013 – en cours`, ou la seule annee connue. */
@Composable
private fun periodeDeDiffusion(anime: Anime): String? {
    val debut = anime.startDate?.year ?: return anime.seasonYear?.toString()
    val fin = anime.endDate?.year

    return when {
        anime.status.equals("RELEASING", ignoreCase = true) -> stringResource(
            R.string.info_range,
            debut.toString(),
            stringResource(R.string.label_ongoing)
        )

        fin != null && fin != debut ->
            stringResource(R.string.info_range, debut.toString(), fin.toString())

        else -> debut.toString()
    }
}

/**
 * Auteur de l'oeuvre d'origine. AniList le declare sous `Original Creator` ou
 * `Original Story`, parfois suivis d'une precision entre parentheses.
 */
private fun createurs(anime: Anime): String? = nomsDuStaff(anime) { role ->
    role.startsWith("Original Creator", true) || role.startsWith("Original Story", true)
}

/**
 * Realisation. Le filtre porte sur le **debut** de l'intitule : `Animation Director`
 * et `Episode Director` sont d'autres metiers, et ne commencent pas par `Director`.
 */
private fun realisateurs(anime: Anime): String? = nomsDuStaff(anime) { role ->
    role.startsWith("Director", true) || role.startsWith("Chief Director", true)
}

private fun nomsDuStaff(anime: Anime, critere: (String) -> Boolean): String? = anime.staff
    .filter { critere(it.role.orEmpty()) }
    .map { it.member.fullName }
    .distinct()
    .take(3)
    .joinToString(", ")
    .ifBlank { null }

/** Une etiquette de la rangee de l'entete, eventuellement mise en avant. */
private data class InfoPuce(val texte: String, val accentuee: Boolean = false)

/** Rangee de l'entete : quelques valeurs, dont une seule peut etre accentuee. */
@Composable
private fun RangeeMeta(puces: List<InfoPuce>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(puces) { puce -> Puce(texte = puce.texte, accentuee = puce.accentuee) }
    }
}

/** Traduit les roles AniList, qui arrivent en anglais et en majuscules. */
@Composable
private fun roleTraduit(role: String?): String? = when (role?.uppercase()) {
    "MAIN" -> stringResource(R.string.role_main)
    "SUPPORTING" -> stringResource(R.string.role_supporting)
    "BACKGROUND" -> stringResource(R.string.role_background)
    else -> role
}

/**
 * Onglets de la fiche, dans leur ordre d'apparition.
 *
 * Un seul est actif a la fois : c'est ce qui separe ce qui sert a regarder de ce
 * qui sert a se renseigner, au lieu de tout empiler dans une meme page.
 */
private enum class OngletFiche(val libelle: Int) {
    EPISODES(R.string.section_episodes),
    SUGGESTIONS(R.string.tab_suggestions),
    PERSONNAGES(R.string.section_voice_actors),
    STAFF(R.string.section_staff),
    DETAILS(R.string.tab_details)
}

/**
 * Barre d'onglets : un libelle par onglet, souligne de corail quand il est actif.
 *
 * L'onglet change **a la validation**, pas au focus. Le changer au passage du focus
 * renouvelait tout le contenu a chaque pression laterale : la liste en dessous se
 * reconstruisait, la page se repositionnait, et le focus n'etait plus la ou la
 * telecommande l'avait laisse. On longe donc la barre librement, et l'on appuie sur
 * OK quand on veut changer de section.
 */
@Composable
private fun BarreOnglets(
    onglets: List<OngletFiche>,
    courant: OngletFiche,
    onChoisir: (OngletFiche) -> Unit,
    modifier: Modifier = Modifier
) {
    val gabarit = LocalGabarit.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = gabarit.margeFin),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            onglets.forEach { onglet ->
                Onglet(
                    libelle = stringResource(onglet.libelle),
                    actif = onglet == courant,
                    onSelection = { onChoisir(onglet) }
                )
            }
        }

        // Le filet court sur toute la largeur : c'est lui qui fait lire la barre
        // comme une separation et non comme une rangee de boutons de plus.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gabarit.margeFin)
                .height(1.dp)
                .background(MistGrey.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun Onglet(libelle: String, actif: Boolean, onSelection: () -> Unit) {
    var focalise by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelection,
        modifier = Modifier.onFocusChanged { focalise = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MistGrey,
            focusedContainerColor = InkElevated,
            focusedContentColor = PureWhite
        ),
        // Aucun agrandissement : un titre qui grossit au passage du focus ferait
        // bouger toute la barre.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        // Largeur intrinseque : sans elle, le soulignement `fillMaxWidth` reclame
        // toute la largeur offerte a la rangee, le premier onglet occupe la barre
        // entiere et les suivants sortent de l'ecran.
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                color = if (actif || focalise) PureWhite else MistGrey,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (actif) Coral else Color.Transparent)
            )
        }
    }
}

/** Rapport largeur/hauteur des jaquettes AniList. */
private const val RATIO_JAQUETTE = 2f / 3f

/** Conversion des secondes de l'historique en minutes affichables. */
private const val SECONDES_PAR_MINUTE = 60

/**
 * Colonnes et largeurs des grilles d'onglets.
 *
 * Chaque total — colonnes, intervalles et marges laterales — garde de la reserve
 * dans les 960 dp d'un televiseur 1080p : une largeur ajustee au dernier pixel
 * ferait disparaitre la derniere colonne sur un appareil un peu plus etroit.
 */
private const val LARGEUR_SUGGESTION = 184

private const val LARGEUR_PERSONNAGE = 400

private const val LARGEUR_STAFF = 156

/** Episodes par plage. Au-dela, la liste se pagine au lieu de s'etirer. */
private const val TAILLE_PLAGE = 30

/** Part de la saison qu'AniList doit illustrer pour que les vignettes soient utilisees. */
private const val COUVERTURE_VIGNETTES = 0.5f

/**
 * Interligne de la page. Regle sur la liste d'episodes, qui en est le plus gros
 * consommateur ; les sections des autres onglets y ajoutent leur propre marge.
 */
private const val ESPACEMENT_PAGE = 14

/** Separateur des resumes : « Saison 1 · VOSTFR · 12 episodes disponibles ». */
private const val SEPARATEUR = " \u00b7 "
