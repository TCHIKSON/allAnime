package com.allanime.tv.ui.lecteur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icones de transport du lecteur, dessinees au trait.
 *
 * Elles sont tracees plutot que chargees depuis `material-icons-extended` : les
 * symboles necessaires (pause, saut de 10 secondes, episode precedent / suivant) n'y
 * sont pas tous, et embarquer le jeu complet pour quatre formes couterait bien plus
 * que ces quelques chemins. Le dessin est exprime en fractions de la taille demandee,
 * donc net a n'importe quelle echelle d'ecran.
 */

/** Taille de reference des icones de la barre de lecture. */
val TAILLE_ICONE: Dp = 26.dp

@Composable
fun IconeLecture(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE) {
    Canvas(modifier = modifier.size(taille)) {
        drawPath(triangleLecture(size.width, size.height), couleur)
    }
}

@Composable
fun IconePause(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE) {
    Canvas(modifier = modifier.size(taille)) {
        val largeurBarre = size.width * 0.17f
        val hauteur = size.height * 0.68f
        val haut = (size.height - hauteur) / 2f

        drawRect(
            color = couleur,
            topLeft = Offset(size.width * 0.26f, haut),
            size = Size(largeurBarre, hauteur)
        )
        drawRect(
            color = couleur,
            topLeft = Offset(size.width * 0.57f, haut),
            size = Size(largeurBarre, hauteur)
        )
    }
}

/**
 * Episode precedent ou suivant : un triangle plaque contre une barre, comme sur
 * n'importe quelle telecommande.
 */
@Composable
fun IconeEpisode(
    versAvant: Boolean,
    couleur: Color,
    modifier: Modifier = Modifier,
    taille: Dp = TAILLE_ICONE
) {
    Canvas(modifier = modifier.size(taille)) {
        miroirSiNecessaire(versAvant) {
            val chemin = Path().apply {
                moveTo(size.width * 0.20f, size.height * 0.18f)
                lineTo(size.width * 0.64f, size.height * 0.50f)
                lineTo(size.width * 0.20f, size.height * 0.82f)
                close()
            }
            drawPath(chemin, couleur)
            drawRect(
                color = couleur,
                topLeft = Offset(size.width * 0.70f, size.height * 0.18f),
                size = Size(size.width * 0.13f, size.height * 0.64f)
            )
        }
    }
}

/**
 * Saut de [secondes] secondes : un cercle ouvert, une pointe de fleche et le nombre
 * au centre. C'est le symbole que tous les services de video a la demande emploient,
 * et il se lit a distance mieux qu'un libelle « + 10 s ».
 */
@Composable
fun IconeSaut(
    secondes: Int,
    versAvant: Boolean,
    couleur: Color,
    modifier: Modifier = Modifier,
    taille: Dp = TAILLE_ICONE
) {
    val mesureur = rememberTextMeasurer()

    Canvas(modifier = modifier.size(taille)) {
        miroirSiNecessaire(versAvant) { cercleFleche(couleur) }
        // Le nombre reste lisible dans le sens de la lecture : il n'est pas mis en
        // miroir avec le cercle.
        nombreCentre(secondes, couleur, mesureur)
    }
}

/** Trace le cercle ouvert vers le haut et sa pointe de fleche, orientes vers l'avant. */
private fun DrawScope.cercleFleche(couleur: Color) {
    val rayon = size.minDimension * 0.36f
    val epaisseur = size.minDimension * 0.10f
    val coin = Offset(size.width / 2f - rayon, size.height / 2f - rayon)

    drawArc(
        color = couleur,
        startAngle = 300f,
        sweepAngle = 285f,
        useCenter = false,
        topLeft = coin,
        size = Size(rayon * 2f, rayon * 2f),
        style = Stroke(width = epaisseur)
    )

    val pointe = Path().apply {
        moveTo(size.width * 0.82f, size.height * 0.19f)
        lineTo(size.width * 0.60f, size.height * 0.08f)
        lineTo(size.width * 0.60f, size.height * 0.30f)
        close()
    }
    drawPath(pointe, couleur)
}

private fun DrawScope.nombreCentre(valeur: Int, couleur: Color, mesureur: TextMeasurer) {
    val mesure = mesureur.measure(
        text = valeur.toString(),
        style = TextStyle(
            color = couleur,
            fontSize = (size.minDimension * 0.40f).toSp(),
            fontWeight = FontWeight.Bold
        )
    )

    drawText(
        textLayoutResult = mesure,
        topLeft = Offset(
            x = (size.width - mesure.size.width) / 2f,
            y = (size.height - mesure.size.height) / 2f
        )
    )
}

/** Le dessin est trace vers l'avant ; le sens inverse est un simple miroir. */
private inline fun DrawScope.miroirSiNecessaire(versAvant: Boolean, trace: DrawScope.() -> Unit) {
    if (versAvant) {
        trace()
    } else {
        scale(scaleX = -1f, scaleY = 1f, pivot = center) { trace() }
    }
}

private fun triangleLecture(largeur: Float, hauteur: Float): Path = Path().apply {
    moveTo(largeur * 0.28f, hauteur * 0.16f)
    lineTo(largeur * 0.82f, hauteur * 0.50f)
    lineTo(largeur * 0.28f, hauteur * 0.84f)
    close()
}

/**
 * Engrenage des reglages : un anneau et ses dents, tracees par rotations
 * successives. Dessine ici pour la meme raison que les autres symboles.
 */
@Composable
fun IconeEngrenage(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE) {
    Canvas(modifier = modifier.size(taille)) {
        val cote = size.minDimension
        val rayonAnneau = cote * 0.27f

        drawCircle(
            color = couleur,
            radius = rayonAnneau,
            center = center,
            style = Stroke(width = cote * 0.13f)
        )

        val largeurDent = cote * 0.12f
        repeat(NOMBRE_DENTS) { rang ->
            rotate(degrees = rang * (360f / NOMBRE_DENTS), pivot = center) {
                drawRect(
                    color = couleur,
                    topLeft = Offset(center.x - largeurDent / 2f, center.y - cote * 0.47f),
                    size = Size(largeurDent, cote * 0.19f)
                )
            }
        }
    }
}

/** Coche de l'option active dans le menu des reglages. */
@Composable
fun IconeCoche(couleur: Color, modifier: Modifier = Modifier, taille: Dp = 20.dp) {
    Canvas(modifier = modifier.size(taille)) {
        val trait: DrawStyle = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round)
        val chemin = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.54f)
            lineTo(size.width * 0.41f, size.height * 0.76f)
            lineTo(size.width * 0.84f, size.height * 0.24f)
        }
        drawPath(chemin, couleur, style = trait)
    }
}

/** Huit dents : assez pour lire un engrenage, assez peu pour rester net a 26 dp. */
private const val NOMBRE_DENTS = 8
