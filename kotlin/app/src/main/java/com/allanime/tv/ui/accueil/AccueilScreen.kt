package com.allanime.tv.ui.accueil

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.ProgressionAnime
import com.allanime.tv.domain.model.SortiePlanning
import com.allanime.tv.ui.components.AfficheCatalogue
import com.allanime.tv.ui.components.BoutonTv
import com.allanime.tv.ui.components.CarteReprise
import com.allanime.tv.ui.components.CarteSortie
import com.allanime.tv.ui.components.ContenuSelonEtat
import com.allanime.tv.ui.components.ImageDistante
import com.allanime.tv.ui.components.IndicateurChargement
import com.allanime.tv.ui.components.LogoAllAnime
import com.allanime.tv.ui.components.TitreSection
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite
import com.allanime.tv.ui.theme.SlateGrey
import kotlinx.coroutines.delay

/**
 * Accueil facon plateforme de streaming (section 6).
 *
 * Trois plans se superposent, du fond vers l'avant :
 *
 *  1. **l'image**, plein cadre, qui suit ce qu'on regarde — la serie mise en avant
 *     tant qu'on est dans la banniere, celle qu'on survole des qu'on descend dans
 *     les rangees ;
 *  2. **les voiles degrades**, qui garantissent la lisibilite du texte quelle que
 *     soit l'affiche derriere ;
 *  3. **le contenu** : la banniere, puis les rangees qui defilent verticalement.
 *
 * La banniere tourne toute seule parmi quelques titres, et **s'arrete des qu'elle a
 * le focus** : on ne change pas une page sous les yeux de quelqu'un qui est en train
 * de la lire.
 *
 * @param margeDebut place laissee au rail de la barre laterale.
 */
@Composable
fun AccueilScreen(
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit,
    onReprendre: (ProgressionAnime) -> Unit,
    viewModel: AccueilViewModel = viewModel(factory = AccueilViewModel.factory())
) {
    val etat by viewModel.etat.collectAsState()
    val reprises by viewModel.reprises.collectAsState()
    val sortiesDuJour by viewModel.sortiesDuJour.collectAsState()
    val rangees by viewModel.rangees.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(InkBackground)) {
        ContenuSelonEtat(
            etat = etat,
            modifier = Modifier.fillMaxSize(),
            surReessayer = { viewModel.charger(rafraichir = true) }
        ) { animes ->
            ContenuAccueil(
                animes = animes,
                reprises = reprises,
                sortiesDuJour = sortiesDuJour,
                rangees = rangees,
                margeDebut = margeDebut,
                onOuvrirCatalogue = onOuvrirCatalogue,
                onReprendre = onReprendre
            )
        }
    }
}

@Composable
private fun ContenuAccueil(
    animes: List<AnimeCatalogue>,
    reprises: List<ProgressionAnime>,
    sortiesDuJour: EtatSortiesDuJour,
    rangees: List<RangeeAccueil>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit,
    onReprendre: (ProgressionAnime) -> Unit
) {
    // La selection mise en avant est tiree du catalogue une fois pour toutes : elle
    // ne doit pas se reordonner sous les yeux de l'utilisateur a chaque recomposition.
    val gabarit = LocalGabarit.current
    val heros = remember(animes) { animes.take(NOMBRE_HEROS) }
    var indexHeros by remember(heros) { mutableIntStateOf(0) }
    var banniereFocalisee by remember { mutableStateOf(false) }

    // Survol d'une rangee : l'image de fond suit, mais la banniere, elle, ne bouge
    // pas — sinon descendre d'une rangee reecrirait tout le haut de l'ecran.
    var survol by remember(animes) { mutableStateOf<AnimeCatalogue?>(null) }

    val heroCourant = heros.getOrNull(indexHeros) ?: animes.first()
    val fond = survol ?: heroCourant

    val bouton = remember { FocusRequester() }
    val etatListe = rememberLazyListState()

    LaunchedEffect(animes) {
        // L'ecran s'ouvre sur la banniere complete : le focus y reste, et les
        // rangees se rejoignent a la fleche du bas.
        runCatching { bouton.requestFocus() }

        // Prendre le focus declenche un « amener a l'ecran » qui fait descendre la
        // liste de la hauteur du bouton. Sur une dalle de 540 dp utiles, cela suffit
        // a faire sortir le titre de la banniere par le haut. On laisse donc passer
        // deux trames — le temps que ce defilement soit applique — puis on remet la
        // liste a son debut.
        withFrameNanos { }
        withFrameNanos { }
        etatListe.scrollToItem(0)
    }

    // Rotation automatique, suspendue tant que la banniere est survolee.
    LaunchedEffect(heros, banniereFocalisee) {
        if (heros.size <= 1 || banniereFocalisee) return@LaunchedEffect
        while (true) {
            delay(DUREE_HEROS_MS)
            indexHeros = (indexHeros + 1) % heros.size
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Plan 1 : l'image, en fondu enchaine d'un titre a l'autre.
        Crossfade(
            targetState = fond,
            animationSpec = tween(durationMillis = 420),
            label = "fond-accueil"
        ) { anime ->
            ImageDistante(
                url = anime.image,
                contentDescription = anime.titre,
                modifier = Modifier.fillMaxSize(),
                coins = 0.dp
            )
        }

        // Plan 2 : les voiles. Un degrade horizontal pour le texte de la banniere,
        // un degrade vertical pour les rangees du bas.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        InkBackground,
                        InkBackground.copy(alpha = 0.92f),
                        InkBackground.copy(alpha = 0.20f)
                    )
                )
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(InkBackground.copy(alpha = 0f), InkBackground)
                    )
                )
        )

        // Logo discret : l'identite de l'application, la barre laterale portant
        // desormais la navigation.
        LogoAllAnime(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = gabarit.margeDebut, end = gabarit.margeFin)
        )

        // Plan 3 : le contenu.
        LazyColumn(
            state = etatListe,
            modifier = Modifier.fillMaxSize(),
            // Marge haute : sur un televiseur, les tout premiers pixels tombent
            // souvent hors de la dalle visible.
            contentPadding = PaddingValues(
                top = gabarit.margeDebut,
                bottom = gabarit.margeFin
            ),
            verticalArrangement = Arrangement.spacedBy(gabarit.espacementSection)
        ) {
            item(key = "banniere") {
                Banniere(
                    anime = heroCourant,
                    rang = indexHeros,
                    total = heros.size,
                    margeDebut = margeDebut,
                    bouton = bouton,
                    onOuvrirFiche = { onOuvrirCatalogue(heroCourant.slug) },
                    surFocus = { focalisee ->
                        banniereFocalisee = focalisee
                        // Revenir sur la banniere rend le fond a la serie mise en
                        // avant : l'ecran retrouve exactement l'etat qu'on y avait
                        // laisse.
                        if (focalisee) survol = null
                    }
                )
            }

            if (reprises.isNotEmpty()) {
                item(key = "reprises") {
                    RangeeReprise(
                        reprises = reprises,
                        margeDebut = margeDebut,
                        onReprendre = onReprendre
                    )
                }
            }

            // Sorties du jour : la rangee reste en place pendant la verification du
            // planning, sinon elle apparaitrait apres coup et deplacerait le focus.
            // Une journee sans sortie garde sa rangee — c'est une reponse ; un
            // planning jamais obtenu la fait disparaitre, faute de reponse a donner.
            if (sortiesDuJour.enChargement || sortiesDuJour.planningConnu) {
                item(key = "sorties-du-jour") {
                    RangeeSortiesDuJour(
                        etat = sortiesDuJour,
                        margeDebut = margeDebut,
                        onOuvrirCatalogue = onOuvrirCatalogue
                    )
                }
            }

            if (sortiesDuJour.ajoutsRecents.isNotEmpty()) {
                item(key = "derniers-ajouts") {
                    RangeeSorties(
                        titre = stringResource(R.string.section_recent_additions),
                        sorties = sortiesDuJour.ajoutsRecents,
                        margeDebut = margeDebut,
                        onOuvrirCatalogue = onOuvrirCatalogue
                    )
                }
            }

            // Classement du catalogue : voisinage du dernier anime ouvert, puis les
            // genres. Un anime n'y figure qu'une fois. Les films, eux, ont leur
            // entree dans la barre laterale.
            items(rangees, key = { it.cle }) { rangee ->
                RangeeAffiches(
                    titre = titreRangee(rangee),
                    animes = rangee.animes,
                    margeDebut = margeDebut,
                    onOuvrirCatalogue = onOuvrirCatalogue,
                    surFocus = { anime -> survol = anime }
                )
            }
        }
    }
}

/**
 * Banniere de mise en avant : le seul bloc de la page a cette echelle.
 *
 * Le titre y est volontairement enorme — c'est ce qui distingue une banniere d'une
 * carte agrandie — et le synopsis tenu a trois lignes : au-dela, personne ne le lit
 * depuis un canape, et la fiche est a une pression de touche.
 */
@Composable
private fun Banniere(
    anime: AnimeCatalogue,
    rang: Int,
    total: Int,
    margeDebut: Dp,
    bouton: FocusRequester,
    onOuvrirFiche: () -> Unit,
    surFocus: (Boolean) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column(
        modifier = Modifier
            .fillMaxWidth(FRACTION_BANNIERE)
            // Hauteur *minimale* et non fixe : un titre long passe sur deux
            // lignes, et une hauteur imposee le ferait deborder du cadre — donc
            // disparaitre, le contenu etant centre verticalement.
            .heightIn(min = gabarit.hauteurBanniere)
            .padding(start = margeDebut + gabarit.margeDebut, end = gabarit.margeDebut),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = anime.titre,
            style = MaterialTheme.typography.displaySmall,
            color = PureWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Genres, type et langues sur une seule ligne. Les separer en trois blocs
        // coutait 80 dp de hauteur, soit un septieme de l'ecran utile, pour une
        // information que l'oeil lit de toute facon d'un seul tenant.
        val reperes = (anime.genres.take(3) + anime.types.take(1) + anime.langues.take(2))
            .filter { it.isNotBlank() }
        if (reperes.isNotEmpty()) {
            Text(
                text = reperes.joinToString(SEPARATEUR),
                style = MaterialTheme.typography.labelMedium,
                color = Coral,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = (8 * gabarit.facteur).dp)
            )
        }

        anime.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Text(
                text = synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MistGrey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = (10 * gabarit.facteur).dp)
            )
        }

        Row(
            modifier = Modifier.padding(top = (16 * gabarit.facteur).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoutonTv(
                texte = stringResource(R.string.action_open_detail),
                onClick = onOuvrirFiche,
                modifier = Modifier
                    .focusRequester(bouton)
                    .onFocusChanged { surFocus(it.isFocused) }
            )

            if (total > 1) {
                Spacer(modifier = Modifier.width(gabarit.margeDebut))
                Pastilles(rang = rang, total = total)
            }
        }
    }
}

/** Reperes de defilement de la banniere : ou l'on en est dans la selection. */
@Composable
private fun Pastilles(rang: Int, total: Int) {
    val gabarit = LocalGabarit.current

    Row(horizontalArrangement = Arrangement.spacedBy((7 * gabarit.facteur).dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(((if (index == rang) 9 else 7) * gabarit.facteur).dp)
                    .clip(CircleShape)
                    .background(if (index == rang) PureWhite else SlateGrey)
            )
        }
    }
}

@Composable
private fun RangeeReprise(
    reprises: List<ProgressionAnime>,
    margeDebut: Dp,
    onReprendre: (ProgressionAnime) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column {
        TitreSection(
            texte = stringResource(R.string.section_continue_watching),
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = (12 * gabarit.facteur).dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte),
            contentPadding = PaddingValues(
                start = margeDebut + gabarit.margeDebut,
                end = gabarit.margeFin,
                top = (8 * gabarit.facteur).dp,
                bottom = (10 * gabarit.facteur).dp
            )
        ) {
            items(reprises, key = { it.animeId }) { progression ->
                CarteReprise(
                    progression = progression,
                    onClick = { onReprendre(progression) },
                    largeur = gabarit.largeurAffiche
                )
            }
        }
    }
}

/**
 * Sorties annoncees pour aujourd'hui, avec leur horaire.
 *
 * Trois etats : la verification du planning en cours, une journee sans sortie — ce
 * qui est une information en soi, et non une panne — et la liste elle-meme.
 */
@Composable
private fun RangeeSortiesDuJour(
    etat: EtatSortiesDuJour,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column {
        TitreSection(
            texte = stringResource(R.string.section_today_releases, etat.jour.libelle),
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = (12 * gabarit.facteur).dp)
        )

        when {
            etat.sorties.isNotEmpty() -> CartesSorties(
                sorties = etat.sorties,
                margeDebut = margeDebut,
                onOuvrirCatalogue = onOuvrirCatalogue
            )

            etat.enChargement -> Row(
                modifier = Modifier.padding(
                    start = margeDebut + gabarit.margeDebut,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IndicateurChargement(taille = (22 * gabarit.facteur).dp)
                Text(
                    text = stringResource(R.string.loading_planning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistGrey,
                    modifier = Modifier.padding(start = (12 * gabarit.facteur).dp)
                )
            }

            else -> Text(
                text = stringResource(R.string.empty_today_releases),
                style = MaterialTheme.typography.bodyMedium,
                color = MistGrey,
                modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = (12 * gabarit.facteur).dp)
            )
        }
    }
}

/** Rangee de sorties avec son titre : sert au jour courant comme aux precedents. */
@Composable
private fun RangeeSorties(
    titre: String,
    sorties: List<SortiePlanning>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column {
        TitreSection(
            texte = titre,
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = (12 * gabarit.facteur).dp)
        )
        CartesSorties(
            sorties = sorties,
            margeDebut = margeDebut,
            onOuvrirCatalogue = onOuvrirCatalogue
        )
    }
}

@Composable
private fun CartesSorties(
    sorties: List<SortiePlanning>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte),
        contentPadding = PaddingValues(
            start = margeDebut + gabarit.margeDebut,
            end = gabarit.margeFin,
            top = (8 * gabarit.facteur).dp,
            bottom = (10 * gabarit.facteur).dp
        )
    ) {
        items(sorties, key = { it.cle }) { sortie ->
            CarteSortie(
                sortie = sortie,
                onClick = { onOuvrirCatalogue(sortie.slug) },
                largeur = gabarit.largeurAffiche
            )
        }
    }
}

/** Rangee d'affiches, reutilisee par toutes les sections de classement. */
@Composable
private fun RangeeAffiches(
    titre: String,
    animes: List<AnimeCatalogue>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit,
    surFocus: (AnimeCatalogue) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column {
        TitreSection(
            texte = titre,
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = (12 * gabarit.facteur).dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte),
            // Le rembourrage vertical laisse la place a l'agrandissement au focus :
            // sans lui, une affiche survolee serait rognee par la rangee voisine.
            contentPadding = PaddingValues(
                start = margeDebut + gabarit.margeDebut,
                end = gabarit.margeFin,
                top = (10 * gabarit.facteur).dp,
                bottom = (12 * gabarit.facteur).dp
            )
        ) {
            items(animes, key = { it.slug }) { anime ->
                AfficheCatalogue(
                    anime = anime,
                    onClick = { onOuvrirCatalogue(anime.slug) },
                    largeur = gabarit.largeurAffiche,
                    lignesTitre = 1,
                    surFocus = { focalisee -> if (focalisee) surFocus(anime) }
                )
            }
        }
    }
}

/** Le libelle depend des ressources de langue, pas du modele. */
@Composable
private fun titreRangee(rangee: RangeeAccueil): String = when (rangee) {
    is RangeeAccueil.Similaires ->
        stringResource(R.string.section_because_watched, rangee.titreSource)

    // Les genres du catalogue sont deja en francais : rien a traduire.
    is RangeeAccueil.Genre -> rangee.genre

    is RangeeAccueil.Autres -> stringResource(R.string.section_others)
}

/** Titres proposes par la banniere, parcourus en boucle. */
private const val NOMBRE_HEROS = 6

/** Temps de lecture laisse a chaque titre de la banniere. */
private const val DUREE_HEROS_MS = 9_000L

/** La banniere n'occupe que la gauche de l'ecran : l'affiche reste visible a droite. */
private const val FRACTION_BANNIERE = 0.62f

/** Separateur des genres de la banniere. */
private const val SEPARATEUR = " • "
