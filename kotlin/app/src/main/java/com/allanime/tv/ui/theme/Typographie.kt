package com.allanime.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

/**
 * Echelle typographique de l'application.
 *
 * Une seule famille, sans empattement, dans la graisse la plus proche de celle des
 * interfaces de television grand public : titres tres gras et tres larges, textes
 * courants nettement plus legers. Le contraste entre les deux fait la hierarchie ;
 * sur un ecran regarde a trois metres, c'est lui qui rend une page lisible d'un coup
 * d'oeil, bien plus que la couleur.
 *
 * **La police n'est pas embarquee.** Celle des grandes plateformes est sous licence
 * proprietaire et ne peut pas etre redistribuee ; [FontFamily.SansSerif] prend donc
 * la sans-serif du televiseur (Roboto sur Android TV), a laquelle on applique ici
 * les tailles, les graisses et l'interlettrage. Embarquer une fonte libre ne
 * demanderait que de remplacer [FAMILLE].
 *
 * Les tailles sont pensees pour du 1080p vu de loin : rien en dessous de 13 sp, et
 * un interligne genereux, l'oeil ne pouvant pas revenir en arriere comme sur un
 * ecran tenu en main.
 *
 * **Toutes suivent le facteur du [com.allanime.tv.ui.theme.Gabarit].** Le `sp`, comme
 * le `dp`, ne normalise pas la taille apparente sur un televiseur : laisser la
 * typographie fixe pendant que la mise en page s'agrandit donnerait un texte qui
 * retrecit a mesure que l'ecran grandit. L'interlettrage est mis a l'echelle pour la
 * meme raison — c'est une distance, pas un reglage absolu.
 */
private val FAMILLE = FontFamily.SansSerif

fun typographiePour(facteur: Float): Typography = Typography(
    // Titre d'une banniere plein cadre : le seul element de la page a cette taille.
    displayLarge = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Black,
        fontSize = (52 * facteur).sp,
        lineHeight = (58 * facteur).sp,
        letterSpacing = (-1 * facteur).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Black,
        fontSize = (42 * facteur).sp,
        lineHeight = (48 * facteur).sp,
        letterSpacing = (-0.8 * facteur).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.ExtraBold,
        fontSize = (34 * facteur).sp,
        lineHeight = (40 * facteur).sp,
        letterSpacing = (-0.5 * facteur).sp
    ),
    // Titre de page : fiche d'une serie, entete d'un ecran.
    headlineLarge = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.ExtraBold,
        fontSize = (30 * facteur).sp,
        lineHeight = (36 * facteur).sp,
        letterSpacing = (-0.4 * facteur).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Bold,
        fontSize = (25 * facteur).sp,
        lineHeight = (31 * facteur).sp,
        letterSpacing = (-0.2 * facteur).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Bold,
        fontSize = (21 * facteur).sp,
        lineHeight = (27 * facteur).sp
    ),
    // Titre de rangee : « Reprendre votre visionnage », « Films ».
    titleLarge = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Bold,
        fontSize = (19 * facteur).sp,
        lineHeight = (25 * facteur).sp,
        letterSpacing = (0.1 * facteur).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.SemiBold,
        fontSize = (17 * facteur).sp,
        lineHeight = (23 * facteur).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.SemiBold,
        fontSize = (15 * facteur).sp,
        lineHeight = (20 * facteur).sp
    ),
    // Texte courant : synopsis, descriptions.
    bodyLarge = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * facteur).sp,
        lineHeight = (25 * facteur).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Normal,
        fontSize = (14 * facteur).sp,
        lineHeight = (21 * facteur).sp
    ),
    bodySmall = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Normal,
        fontSize = (13 * facteur).sp,
        lineHeight = (19 * facteur).sp
    ),
    // Libelles : boutons, puces, entetes de metadonnees.
    labelLarge = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.SemiBold,
        fontSize = (15 * facteur).sp,
        lineHeight = (20 * facteur).sp,
        letterSpacing = (0.2 * facteur).sp
    ),
    labelMedium = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.SemiBold,
        fontSize = (13 * facteur).sp,
        lineHeight = (17 * facteur).sp,
        letterSpacing = (0.3 * facteur).sp
    ),
    // Le plus petit corps de l'interface : libelle de metadonnee, en capitales
    // espacees pour rester lisible malgre sa taille.
    labelSmall = TextStyle(
        fontFamily = FAMILLE,
        fontWeight = FontWeight.Bold,
        fontSize = (12 * facteur).sp,
        lineHeight = (16 * facteur).sp,
        letterSpacing = (1 * facteur).sp
    )
)
