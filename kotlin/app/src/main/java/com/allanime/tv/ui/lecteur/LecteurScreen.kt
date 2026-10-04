package com.allanime.tv.ui.lecteur

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.domain.model.InventaireQualites
import com.allanime.tv.domain.model.Lecteur
import com.allanime.tv.domain.model.SelectionLecture
import com.allanime.tv.player.WebPlayerHost
import com.allanime.tv.ui.components.BlocChargement
import com.allanime.tv.ui.components.BlocMessage
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite
import kotlinx.coroutines.delay

/**
 * Lecteur video integre a la fenetre principale (sections 13, 14, 25 et 26).
 *
 * Le WebView occupe tout l'ecran ; une barre de commandes se superpose et s'efface
 * apres quelques secondes d'inactivite. Les reglages qui ne servent pas a chaque
 * instant — source et resolution — sont regroupes derriere l'engrenage, ce qui laisse
 * la barre aux seules commandes de transport.
 *
 * Aucune URL brute n'est montree : les lecteurs sont designes par leur rang et le nom
 * court de leur plateforme.
 */
@Composable
fun LecteurScreen(
    animeId: Int,
    indexSaison: Int,
    codeLangue: String,
    numeroEpisode: Int,
    onRetour: () -> Unit,
    viewModel: LecteurViewModel = viewModel(
        key = "lecteur-$animeId-$indexSaison-$codeLangue",
        factory = LecteurViewModel.factory(
            animeId = animeId,
            indexSaison = indexSaison,
            codeLangue = codeLangue,
            numeroEpisode = numeroEpisode
        )
    )
) {
    val etat by viewModel.etat.collectAsState()
    val indexLecteur by viewModel.indexLecteur.collectAsState()
    val positionReprise by viewModel.positionReprise.collectAsState()

    val selection = (etat as? UiState.Success)?.data
    val url = selection?.episode?.lecteurs?.getOrNull(indexLecteur)?.url

    SurfaceLecteur(
        url = url,
        titre = selection?.animeTitre.orEmpty(),
        sousTitre = selection?.let { reperage(it) }.orEmpty(),
        positionReprise = positionReprise,
        options = selection?.let {
            OptionsLecteur(
                lecteurs = it.episode.lecteurs,
                indexLecteur = indexLecteur,
                surChoisirLecteur = viewModel::changerLecteur,
                surEpisodePrecedent = { viewModel.episodePrecedent() },
                surEpisodeSuivant = { viewModel.episodeSuivant() }
            )
        },
        messageEchec = R.string.player_all_failed,
        onRetour = onRetour,
        surFinDeLecture = { viewModel.episodeSuivant() },
        surEchecLecteur = { viewModel.echecLecteurCourant() },
        surLectureConfirmee = viewModel::succesLecteurCourant,
        surProgressionRapportee = viewModel::surProgression,
        contenuEtat = {
            // Resolution de l'episode : chargement, echec ou absence de lecteur.
            // Ces etats s'affichent par-dessus le WebView, encore vide.
            when {
                etat is UiState.Loading -> BlocChargement((etat as UiState.Loading).messageRes)

                etat is UiState.Empty -> BlocMessage(
                    message = stringResource((etat as UiState.Empty).messageRes)
                )

                etat is UiState.Failure -> BlocMessage(
                    message = stringResource((etat as UiState.Failure).error.messageRes),
                    libelleAction = stringResource(R.string.action_back),
                    action = onRetour
                )

                // L'episode ne propose aucun lecteur : il n'y a rien a charger.
                url == null -> BlocMessage(
                    message = stringResource(R.string.empty_players),
                    libelleAction = stringResource(R.string.action_back),
                    action = onRetour
                )
            }
        }
    )
}

/**
 * Tout ce que la barre de commandes peut piloter en dehors de la lecture elle-meme.
 * Vaut `null` pour la bande-annonce, qui n'a ni lecteur alternatif ni episode voisin.
 */
private data class OptionsLecteur(
    val lecteurs: List<Lecteur>,
    val indexLecteur: Int,
    val surChoisirLecteur: (Int) -> Unit,
    val surEpisodePrecedent: () -> Unit,
    val surEpisodeSuivant: () -> Unit
)

/**
 * Hote du WebView, de la barre de commandes et du panneau des reglages.
 *
 * Isole du ViewModel pour rester une pure affaire d'interface : cycle de vie du
 * WebView, plein ecran, visibilite des commandes, position de lecture, qualites
 * proposees par la page et touches de la telecommande.
 */
@Composable
private fun SurfaceLecteur(
    url: String?,
    titre: String,
    sousTitre: String,
    positionReprise: Int,
    options: OptionsLecteur?,
    messageEchec: Int,
    onRetour: () -> Unit,
    surFinDeLecture: () -> Unit,
    surEchecLecteur: () -> Boolean,
    surLectureConfirmee: () -> Unit,
    surProgressionRapportee: (Int, Int) -> Unit,
    contenuEtat: @Composable () -> Unit
) {
    val contexte = LocalContext.current
    val proprietaireCycleDeVie = LocalLifecycleOwner.current

    var chargement by remember { mutableStateOf(true) }
    var pleinEcran by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var controlesVisibles by remember { mutableStateOf(true) }
    var menuOuvert by remember { mutableStateOf(false) }
    var compteurActivite by remember { mutableIntStateOf(0) }
    var enLecture by remember { mutableStateOf(true) }
    var position by remember { mutableIntStateOf(0) }
    var duree by remember { mutableIntStateOf(0) }
    var qualites by remember { mutableStateOf(InventaireQualites.VIDE) }

    val surActivite = { compteurActivite++; controlesVisibles = true }

    val hote = remember {
        WebPlayerHost(
            context = contexte,
            ecouteur = object : WebPlayerHost.Ecouteur {
                override fun surChargement(enCours: Boolean) {
                    chargement = enCours
                }

                override fun surErreur(description: String?) {
                    // Section 13 : bascule automatique vers le lecteur suivant tant
                    // qu'il en reste un.
                    message = if (surEchecLecteur()) {
                        R.string.player_switching
                    } else {
                        R.string.error_player_unreachable
                    }
                }

                override fun surPopupBloque() {
                    message = R.string.player_popup_blocked
                }

                override fun surRedirectionBloquee(url: String) {
                    message = R.string.player_redirect_blocked
                }

                override fun surOverlaysMasques(nombre: Int) = Unit

                override fun surVideoDetectee() {
                    chargement = false
                }

                override fun surAutoplayRefuse() {
                    // Un lecteur qui ne demarre pas tout seul n'a pas a etre relance a
                    // la main : on passe au suivant, et on ne propose le bouton Lire
                    // que lorsqu'il n'y a plus aucune source a essayer.
                    if (surEchecLecteur()) {
                        message = R.string.player_switching
                    } else {
                        message = R.string.player_autoplay_blocked
                        enLecture = false
                    }
                }

                override fun surLectureTerminee() {
                    surFinDeLecture()
                }

                override fun surPleinEcranHtml5(actif: Boolean) {
                    pleinEcran = actif
                    if (actif) {
                        controlesVisibles = false
                        menuOuvert = false
                    }
                }

                override fun surLectureDemarree() {
                    // Des images defilent : ce lecteur est bon, on le note comme tel.
                    chargement = false
                    surLectureConfirmee()
                }

                override fun surDemarrageImpossible() {
                    // Le libelle final depend de ce qu'on lisait : une bande-annonce
                    // n'a pas de « lecteur suivant » a tenter, et parler d'episode
                    // n'y voudrait rien dire.
                    message = if (surEchecLecteur()) {
                        R.string.player_too_slow
                    } else {
                        messageEchec
                    }
                }

                override fun surLecteurEnCadreExterne() {
                    // Surtout pas d'echec ici : la video joue peut-etre parfaitement
                    // dans ce cadre, et basculer sur un autre lecteur couperait une
                    // lecture en cours. On arrete simplement d'attendre, et on previent
                    // que les commandes de l'application n'y repondront pas.
                    chargement = false
                    message = R.string.player_external_frame
                }

                override fun surQualites(inventaire: InventaireQualites) {
                    qualites = inventaire
                }

                override fun surProgression(positionSecondes: Int, dureeSecondes: Int) {
                    position = positionSecondes
                    if (dureeSecondes > 0) duree = dureeSecondes
                    surProgressionRapportee(positionSecondes, dureeSecondes)
                }

                override fun surEtatLecture(lectureEnCours: Boolean) {
                    enLecture = lectureEnCours
                }
            }
        )
    }

    // Cycle de vie : le WebView est mis en pause avec l'ecran et libere a la sortie.
    DisposableEffect(proprietaireCycleDeVie) {
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_PAUSE -> hote.surPause()
                Lifecycle.Event.ON_RESUME -> hote.surReprise()
                else -> Unit
            }
        }
        proprietaireCycleDeVie.lifecycle.addObserver(observateur)

        onDispose {
            proprietaireCycleDeVie.lifecycle.removeObserver(observateur)
            hote.detruire()
        }
    }

    LaunchedEffect(url) {
        url?.let {
            chargement = true
            position = 0
            duree = 0
            enLecture = true
            // Les qualites appartiennent a la page : elles n'ont aucun sens d'un
            // lecteur a l'autre.
            qualites = InventaireQualites.VIDE
            hote.charger(it)
        }
    }

    // Reprise : des que la video repond, on se replace ou l'utilisateur s'etait
    // arrete. Un seul saut par chargement, sinon on lutterait contre l'utilisateur.
    var repriseAppliquee by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, positionReprise, chargement) {
        if (!repriseAppliquee && !chargement && positionReprise > 0) {
            repriseAppliquee = true
            delay(600)
            hote.allerA(positionReprise)
        }
    }

    // Effacement automatique du message d'information.
    LaunchedEffect(message) {
        if (message != null) {
            delay(DUREE_MESSAGE_MS)
            message = null
        }
    }

    // Masquage automatique des commandes apres inactivite. Le panneau des reglages
    // suspend ce compte a rebours : il disparaitrait sous les doigts.
    LaunchedEffect(compteurActivite, controlesVisibles, menuOuvert) {
        if (controlesVisibles && !menuOuvert) {
            delay(DUREE_CONTROLES_MS)
            controlesVisibles = false
        }
    }

    BackHandler {
        // Reglages -> plein ecran HTML5 -> lecteur normal -> page de la serie.
        when {
            menuOuvert -> menuOuvert = false
            hote.quitterPleinEcranHtml5() -> Unit
            else -> onRetour()
        }
    }

    // Deux points de focus possibles : l'ancre invisible quand les commandes sont
    // masquees, le bouton Lecture / Pause quand elles sont affichees. Sans cela le
    // focus se perd a la disparition des commandes et la telecommande ne repond plus.
    // Le panneau des reglages, lui, place son propre focus.
    val ancre = remember { FocusRequester() }
    val boutonLecture = remember { FocusRequester() }

    LaunchedEffect(controlesVisibles, pleinEcran, menuOuvert) {
        when {
            menuOuvert -> Unit
            controlesVisibles && !pleinEcran -> {
                // Laisse le temps aux boutons d'entrer en composition.
                delay(80)
                runCatching { boutonLecture.requestFocus() }
            }

            else -> runCatching { ancre.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { evenement ->
                if (evenement.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // L'etat d'avant la touche decide du role des fleches ; l'appui,
                // lui, compte toujours comme une activite — y compris quand il ne
                // sert qu'a passer d'un bouton au suivant, sans quoi la barre
                // s'efface pendant qu'on la parcourt.
                val commandesOuvertes = controlesVisibles
                surActivite()

                when (evenement.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        // L'etat du bouton suit les evenements de la video, pas le clic.
                        hote.basculerLecture()
                        true
                    }

                    Key.MediaFastForward -> {
                        hote.avancer(SAUT_SECONDES)
                        true
                    }

                    Key.MediaRewind -> {
                        hote.avancer(-SAUT_SECONDES)
                        true
                    }

                    // Commandes masquees : les fleches pilotent la lecture. Commandes
                    // visibles, elles retrouvent leur role de navigation au focus.
                    Key.DirectionRight -> if (!commandesOuvertes) {
                        hote.avancer(SAUT_SECONDES)
                        true
                    } else {
                        false
                    }

                    Key.DirectionLeft -> if (!commandesOuvertes) {
                        hote.avancer(-SAUT_SECONDES)
                        true
                    } else {
                        false
                    }

                    // Sur un ecran nu, la touche centrale ne fait que rappeler la
                    // barre : elle ne doit pas activer le bouton qui vient d'y
                    // reprendre le focus.
                    Key.DirectionCenter, Key.Enter -> !commandesOuvertes

                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { hote.conteneur },
            modifier = Modifier.fillMaxSize()
        )

        // Ancre de focus, invisible : retient le focus quand aucune commande n'est
        // affichee, pour que les touches de la telecommande restent ecoutees.
        Box(
            modifier = Modifier
                .size(1.dp)
                .focusRequester(ancre)
                .focusable()
        )

        contenuEtat()

        if (chargement) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                BlocChargement(R.string.loading_player)
            }
        }

        message?.let { texte ->
            Text(
                text = stringResource(texte),
                style = MaterialTheme.typography.labelLarge,
                color = PureWhite,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(InkBackground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        if (controlesVisibles && !pleinEcran && !menuOuvert) {
            BarreCommandes(
                titre = titre,
                sousTitre = sousTitre,
                position = position,
                duree = duree,
                enLecture = enLecture,
                options = options,
                boutonLecture = boutonLecture,
                surReculer = { surActivite(); hote.avancer(-SAUT_SECONDES) },
                surBasculer = { surActivite(); hote.basculerLecture() },
                surAvancer = { surActivite(); hote.avancer(SAUT_SECONDES) },
                surOuvrirReglages = { surActivite(); menuOuvert = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (menuOuvert && options != null) {
            MenuReglages(
                lecteurs = options.lecteurs,
                indexLecteur = options.indexLecteur,
                qualites = qualites,
                surChoisirLecteur = { index ->
                    menuOuvert = false
                    surActivite()
                    options.surChoisirLecteur(index)
                },
                surChoisirQualite = { id ->
                    surActivite()
                    hote.choisirQualite(id)
                }
            )
        }
    }
}

/**
 * Barre de commandes : titre, avancement, puis les commandes de transport sur une
 * seule ligne, toutes atteignables avec les fleches gauche et droite.
 */
@Composable
private fun BarreCommandes(
    titre: String,
    sousTitre: String,
    position: Int,
    duree: Int,
    enLecture: Boolean,
    options: OptionsLecteur?,
    boutonLecture: FocusRequester,
    surReculer: () -> Unit,
    surBasculer: () -> Unit,
    surAvancer: () -> Unit,
    surOuvrirReglages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        InkBackground.copy(alpha = 0.75f),
                        InkBackground.copy(alpha = 0.97f)
                    )
                )
            )
            .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = titre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sousTitre.isNotBlank()) {
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.labelLarge,
                    color = MistGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        BarreProgression(position = position, duree = duree)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoutonIcone(
                description = stringResource(R.string.action_rewind),
                onClick = surReculer
            ) { couleur -> IconeSaut(SAUT_SECONDES, versAvant = false, couleur = couleur) }

            BoutonIcone(
                description = stringResource(
                    if (enLecture) R.string.action_pause else R.string.action_play
                ),
                onClick = surBasculer,
                modifier = Modifier.focusRequester(boutonLecture)
            ) { couleur ->
                if (enLecture) IconePause(couleur) else IconeLecture(couleur)
            }

            BoutonIcone(
                description = stringResource(R.string.action_forward),
                onClick = surAvancer
            ) { couleur -> IconeSaut(SAUT_SECONDES, versAvant = true, couleur = couleur) }

            if (options != null) {
                Spacer(modifier = Modifier.width(20.dp))

                BoutonIcone(
                    description = stringResource(R.string.action_previous_episode),
                    onClick = options.surEpisodePrecedent
                ) { couleur -> IconeEpisode(versAvant = false, couleur = couleur) }

                BoutonIcone(
                    description = stringResource(R.string.action_next_episode),
                    onClick = options.surEpisodeSuivant
                ) { couleur -> IconeEpisode(versAvant = true, couleur = couleur) }

                Spacer(modifier = Modifier.weight(1f))

                BoutonIcone(
                    description = stringResource(R.string.action_settings),
                    onClick = surOuvrirReglages
                ) { couleur -> IconeEngrenage(couleur) }
            }
        }
    }
}

/**
 * Bouton rond de la barre de lecture. La couleur du symbole est passee au contenu :
 * le focus doit se voir sur l'icone autant que sur le fond.
 */
@Composable
private fun BoutonIcone(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable (Color) -> Unit
) {
    var focalise by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(50)

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(TAILLE_BOUTON.dp)
            .onFocusChanged { focalise = it.isFocused }
            .semantics { contentDescription = description },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = InkElevated.copy(alpha = 0.9f),
            contentColor = PureWhite,
            focusedContainerColor = Coral,
            focusedContentColor = PureWhite
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, PureWhite), shape = forme)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            contenu(if (focalise) PureWhite else MistGrey)
        }
    }
}

/** Barre d'avancement et minutage de l'episode en cours. */
@Composable
private fun BarreProgression(position: Int, duree: Int) {
    val avancement = if (duree > 0) (position.toFloat() / duree).coerceIn(0f, 1f) else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = formaterDuree(position),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = PureWhite
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MistGrey.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(avancement)
                    .background(Coral)
            )
        }

        Text(
            text = if (duree > 0) formaterDuree(duree) else "--:--",
            style = MaterialTheme.typography.labelMedium,
            color = MistGrey
        )
    }
}

/**
 * Lecture d'une URL unique, sans catalogue : sert a la bande-annonce AniList
 * (section 8). Elle reutilise exactement le meme WebView protege que les episodes.
 */
@Composable
fun LecteurDirectScreen(
    url: String,
    titre: String,
    onRetour: () -> Unit
) {
    SurfaceLecteur(
        url = url.takeIf { it.isNotBlank() },
        titre = titre.ifBlank { stringResource(R.string.action_watch_trailer) },
        sousTitre = if (titre.isBlank()) "" else stringResource(R.string.action_watch_trailer),
        positionReprise = 0,
        options = null,
        messageEchec = R.string.error_trailer_unavailable,
        onRetour = onRetour,
        surFinDeLecture = onRetour,
        surEchecLecteur = { false },
        surLectureConfirmee = { },
        surProgressionRapportee = { _, _ -> },
        contenuEtat = {
            if (url.isBlank()) {
                BlocMessage(
                    message = stringResource(R.string.error_not_found),
                    libelleAction = stringResource(R.string.action_back),
                    action = onRetour
                )
            }
        }
    )
}

/** `Saison 1 · VOSTFR · Épisode 3` */
@Composable
private fun reperage(selection: SelectionLecture): String = stringResource(
    R.string.player_position,
    selection.saison.nom,
    selection.langue.code.label,
    stringResource(R.string.label_episode, selection.episode.numero)
)

/** `9:05` ou `1:09:05` selon la duree. */
private fun formaterDuree(secondes: Int): String {
    val total = secondes.coerceAtLeast(0)
    val heures = total / 3600
    val minutes = (total % 3600) / 60
    val restes = total % 60

    return if (heures > 0) {
        "%d:%02d:%02d".format(heures, minutes, restes)
    } else {
        "%d:%02d".format(minutes, restes)
    }
}

private const val DUREE_CONTROLES_MS = 6_000L
private const val DUREE_MESSAGE_MS = 3_000L
private const val SAUT_SECONDES = 10

/** Diametre des boutons de transport : confortable a viser a la telecommande. */
private const val TAILLE_BOUTON = 52
