package com.allanime.tv.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icones de la barre laterale, dessinees au trait.
 *
 * Meme parti pris que pour les icones du lecteur : les formes sont tracees en
 * fractions de la taille demandee plutot que chargees depuis un jeu d'icones
 * complet. Cinq symboles ne justifient pas d'embarquer `material-icons-extended`,
 * et un trace reste net a n'importe quelle definition d'ecran.
 *
 * Toutes partagent la meme epaisseur de trait et le meme cadre, sans quoi la
 * colonne paraitrait irreguliere alors que les icones sont alignees.
 */

/** Taille de reference des icones de navigation. */
val TAILLE_ICONE_NAV: Dp = 24.dp

/** Epaisseur commune, exprimee en fraction du cadre. */
private const val TRAIT = 0.085f

@Composable
fun IconeRecherche(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE_NAV) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * TRAIT
        val rayon = size.minDimension * 0.29f
        val centre = Offset(size.width * 0.42f, size.height * 0.42f)

        drawCircle(
            color = couleur,
            radius = rayon,
            center = centre,
            style = Stroke(width = trait, cap = StrokeCap.Round)
        )
        // Le manche part du bord du cercle, pas de son centre : sinon le trait
        // traverse la loupe et la forme devient illisible en petit.
        val depart = Offset(
            centre.x + rayon * 0.72f,
            centre.y + rayon * 0.72f
        )
        drawLine(
            color = couleur,
            start = depart,
            end = Offset(size.width * 0.86f, size.height * 0.86f),
            strokeWidth = trait,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconeAccueil(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE_NAV) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * TRAIT

        // Toit : deux pans partant du faite.
        val toit = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.46f)
            lineTo(size.width * 0.50f, size.height * 0.13f)
            lineTo(size.width * 0.90f, size.height * 0.46f)
        }
        drawPath(toit, couleur, style = Stroke(width = trait, cap = StrokeCap.Round))

        // Murs : deux montants relies par le sol.
        val murs = Path().apply {
            moveTo(size.width * 0.21f, size.height * 0.42f)
            lineTo(size.width * 0.21f, size.height * 0.86f)
            lineTo(size.width * 0.79f, size.height * 0.86f)
            lineTo(size.width * 0.79f, size.height * 0.42f)
        }
        drawPath(murs, couleur, style = Stroke(width = trait, cap = StrokeCap.Round))
    }
}

/**
 * Series : un ecran de television pose sur son pied, avec ses deux antennes.
 * C'est le symbole que les interfaces TV emploient pour designer un feuilleton.
 */
@Composable
fun IconeSeries(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE_NAV) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * TRAIT

        drawRoundRect(
            color = couleur,
            topLeft = Offset(size.width * 0.12f, size.height * 0.36f),
            size = Size(size.width * 0.76f, size.height * 0.46f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trait * 1.6f),
            style = Stroke(width = trait)
        )
        // Antennes : deux obliques qui se rejoignent au sommet de l'ecran.
        drawLine(
            color = couleur,
            start = Offset(size.width * 0.32f, size.height * 0.16f),
            end = Offset(size.width * 0.50f, size.height * 0.34f),
            strokeWidth = trait,
            cap = StrokeCap.Round
        )
        drawLine(
            color = couleur,
            start = Offset(size.width * 0.68f, size.height * 0.16f),
            end = Offset(size.width * 0.50f, size.height * 0.34f),
            strokeWidth = trait,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Films : une bobine de pellicule, reconnaissable a ses perforations laterales.
 */
@Composable
fun IconeFilms(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE_NAV) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * TRAIT

        drawRoundRect(
            color = couleur,
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = Size(size.width * 0.76f, size.height * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trait * 1.6f),
            style = Stroke(width = trait)
        )

        // Perforations : trois de chaque cote, comme sur une pellicule 35 mm.
        val cote = size.width * 0.10f
        val hauteurTrou = size.height * 0.11f
        listOf(0.29f, 0.45f, 0.61f).forEach { y ->
            drawRect(
                color = couleur,
                topLeft = Offset(size.width * 0.20f - cote / 2f, size.height * y),
                size = Size(cote, hauteurTrou)
            )
            drawRect(
                color = couleur,
                topLeft = Offset(size.width * 0.80f - cote / 2f, size.height * y),
                size = Size(cote, hauteurTrou)
            )
        }
    }
}

/**
 * Planning : une page de calendrier, avec sa reliure et son trait d'entete.
 */
@Composable
fun IconePlanning(couleur: Color, modifier: Modifier = Modifier, taille: Dp = TAILLE_ICONE_NAV) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * TRAIT

        drawRoundRect(
            color = couleur,
            topLeft = Offset(size.width * 0.14f, size.height * 0.22f),
            size = Size(size.width * 0.72f, size.height * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trait * 1.6f),
            style = Stroke(width = trait)
        )
        // Trait d'entete : separe le mois des jours, comme sur un calendrier mural.
        drawLine(
            color = couleur,
            start = Offset(size.width * 0.14f, size.height * 0.40f),
            end = Offset(size.width * 0.86f, size.height * 0.40f),
            strokeWidth = trait
        )
        // Reliure : les deux anneaux qui depassent en haut.
        listOf(0.34f, 0.66f).forEach { x ->
            drawLine(
                color = couleur,
                start = Offset(size.width * x, size.height * 0.12f),
                end = Offset(size.width * x, size.height * 0.30f),
                strokeWidth = trait,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Chevron de repli, oriente vers la gauche ou vers la droite. */
@Composable
fun IconeChevron(
    couleur: Color,
    versDroite: Boolean,
    modifier: Modifier = Modifier,
    taille: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * 0.14f
        val x = if (versDroite) 0.35f else 0.65f
        val xFin = if (versDroite) 0.65f else 0.35f

        val chemin = Path().apply {
            moveTo(size.width * x, size.height * 0.22f)
            lineTo(size.width * xFin, size.height * 0.50f)
            lineTo(size.width * x, size.height * 0.78f)
        }
        drawPath(chemin, couleur, style = Stroke(width = trait, cap = StrokeCap.Round))
    }
}

/** Efface le dernier caractere : une gomme flechee, comme sur un clavier physique. */
@Composable
fun IconeEffacer(couleur: Color, modifier: Modifier = Modifier, taille: Dp = 22.dp) {
    Canvas(modifier = modifier.size(taille)) {
        val trait = size.minDimension * 0.09f

        // Le contour : un rectangle dont le cote gauche se termine en pointe.
        val contour = Path().apply {
            moveTo(size.width * 0.36f, size.height * 0.22f)
            lineTo(size.width * 0.90f, size.height * 0.22f)
            lineTo(size.width * 0.90f, size.height * 0.78f)
            lineTo(size.width * 0.36f, size.height * 0.78f)
            lineTo(size.width * 0.10f, size.height * 0.50f)
            close()
        }
        drawPath(contour, couleur, style = Stroke(width = trait))

        // La croix interieure.
        drawCroix(couleur, trait)
    }
}

private fun DrawScope.drawCroix(couleur: Color, trait: Float) {
    drawLine(
        color = couleur,
        start = Offset(size.width * 0.50f, size.height * 0.38f),
        end = Offset(size.width * 0.74f, size.height * 0.62f),
        strokeWidth = trait,
        cap = StrokeCap.Round
    )
    drawLine(
        color = couleur,
        start = Offset(size.width * 0.74f, size.height * 0.38f),
        end = Offset(size.width * 0.50f, size.height * 0.62f),
        strokeWidth = trait,
        cap = StrokeCap.Round
    )
}
