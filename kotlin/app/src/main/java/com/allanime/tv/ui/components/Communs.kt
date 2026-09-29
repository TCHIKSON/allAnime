package com.allanime.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/** Indicateur de chargement aux couleurs de la marque. */
@Composable
fun IndicateurChargement(modifier: Modifier = Modifier, taille: Dp = 44.dp) {
    val transition = rememberInfiniteTransition(label = "chargement")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Canvas(modifier = modifier.size(taille)) {
        drawArc(
            color = Coral,
            startAngle = angle,
            sweepAngle = 96f,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Bloc de chargement avec le libelle de l'operation en cours (section 29). */
@Composable
fun BlocChargement(messageRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IndicateurChargement()
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            color = MistGrey,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * Bloc de message centre : etat vide ou erreur, avec action de reprise optionnelle.
 *
 * L'illustration de marque precede le message : une rangee vide, un episode
 * introuvable ou un lecteur injoignable se reconnaissent alors avant d'etre lus, et
 * une page qui n'a rien a montrer ne ressemble plus a une page mal chargee. Elle
 * peut etre retiree la ou la hauteur disponible ne la permet pas.
 */
@Composable
fun BlocMessage(
    message: String,
    modifier: Modifier = Modifier,
    libelleAction: String? = null,
    action: (() -> Unit)? = null,
    illustration: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration) {
            Image(
                painter = painterResource(R.drawable.app_logo_no_data),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(bottom = 18.dp).height((HAUTEUR_ILLUSTRATION * LocalGabarit.current.facteur).dp)
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MistGrey,
            textAlign = TextAlign.Center
        )
        if (action != null && libelleAction != null) {
            BoutonTv(
                texte = libelleAction,
                onClick = action,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}

/**
 * Rend l'etat d'un chargement : en cours, echec, vide ou contenu.
 * Centralise le traitement des quatre etats demandes par la section 2.
 */
@Composable
fun <T> ContenuSelonEtat(
    etat: UiState<T>,
    modifier: Modifier = Modifier,
    surReessayer: (() -> Unit)? = null,
    contenu: @Composable (T) -> Unit
) {
    when (etat) {
        is UiState.Loading -> BlocChargement(etat.messageRes, modifier)
        is UiState.Empty -> BlocMessage(stringResource(etat.messageRes), modifier)
        is UiState.Failure -> BlocMessage(
            message = stringResource(etat.error.messageRes),
            modifier = modifier,
            libelleAction = surReessayer?.let { stringResource(R.string.action_retry) },
            action = surReessayer
        )

        is UiState.Success -> contenu(etat.data)
    }
}

/**
 * Image distante avec ses trois etats : chargement, image manquante et echec
 * (section 2). Une URL absente n'est jamais remplacee par une image inventee.
 */
@Composable
fun ImageDistante(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    coins: Dp = 12.dp,
    flou: Boolean = false
) {
    val forme = RoundedCornerShape(coins)

    if (url.isNullOrBlank()) {
        PlaceholderImage(modifier = modifier.clip(forme))
        return
    }

    // Le flou est applique au decodage et non a l'affichage : `Modifier.blur`
    // demande Android 12, et resterait sans effet sur la plupart des televiseurs.
    val contexte = LocalContext.current
    val modele = remember(url, flou) {
        ImageRequest.Builder(contexte)
            .data(url)
            .apply { if (flou) transformations(FlouTransformation()) }
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = modele,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(forme),
        loading = {
            Box(
                modifier = Modifier.fillMaxSize().background(InkElevated),
                contentAlignment = Alignment.Center
            ) {
                IndicateurChargement(taille = 28.dp)
            }
        },
        error = { PlaceholderImage(modifier = Modifier.fillMaxSize()) }
    )
}

@Composable
private fun PlaceholderImage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(InkElevated),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.label_no_image),
            style = MaterialTheme.typography.labelMedium,
            color = MistGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/** Titre de section, lisible a distance. */
@Composable
fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = PureWhite,
        modifier = modifier
    )
}

/**
 * Petite etiquette d'information (genre, statut, langue...).
 *
 * [accentuee] reserve le corail a la seule valeur qu'on cherche du regard sur une
 * fiche — la note — pour qu'elle ressorte d'une rangee d'etiquettes grises.
 */
@Composable
fun Puce(texte: String, modifier: Modifier = Modifier, accentuee: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (accentuee) Coral else InkElevated)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (accentuee) FontWeight.Bold else FontWeight.Normal,
            color = if (accentuee) PureWhite else MistGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Couple libelle / valeur du tableau d'informations de la fiche.
 *
 * Le libelle est plus petit et plus sombre que la valeur : a trois metres, l'oeil
 * saute d'une valeur a l'autre et ne lit les libelles qu'en cas de doute.
 */
@Composable
fun ChampInformation(libelle: String, valeur: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MistGrey
        )
        Text(
            text = valeur,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = PureWhite,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Logo de l'application, tel qu'il est fourni dans `drawable/app_logo`.
 *
 * Seule la hauteur est imposee : la largeur suit le rapport de l'image, pour qu'un
 * nouvel export ne demande aucune retouche ici.
 */
@Composable
fun LogoAllAnime(
    modifier: Modifier = Modifier,
    hauteur: Dp = (HAUTEUR_LOGO * LocalGabarit.current.facteur).dp
) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = stringResource(R.string.cd_logo),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(hauteur)
    )
}

/** Hauteur du logo dans les barres de navigation. */
private const val HAUTEUR_LOGO = 42

/** Hauteur de l'illustration des etats sans contenu. */
private const val HAUTEUR_ILLUSTRATION = 148
