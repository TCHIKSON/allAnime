package com.allanime.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.domain.model.CharacterRole
import com.allanime.tv.domain.model.StaffRole
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Bulle ronde : le motif visuel des portraits sur la fiche detaillee.
 * Les personnages, les doubleurs et le staff partagent la meme forme.
 */
@Composable
fun BulleRonde(
    url: String?,
    contentDescription: String?,
    taille: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(taille)
            .clip(CircleShape)
            .background(InkElevated),
        contentAlignment = Alignment.Center
    ) {
        ImageDistante(
            url = url,
            contentDescription = contentDescription,
            modifier = Modifier.size(taille),
            coins = taille / 2
        )
    }
}

/**
 * Personnage et sa voix, face a face.
 *
 * Le personnage occupe la gauche, le comedien de doublage la droite : les deux
 * informations restent distinctes mais lisibles d'un seul coup d'oeil, sans
 * melanger la fiche des personnages et celle du staff.
 */
@Composable
fun CartePersonnageVoix(
    role: CharacterRole,
    roleTraduit: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 380.dp
) {
    val voix = role.voiceActors.firstOrNull()

    CarteFocalisable(
        onClick = onClick,
        modifier = modifier.width(largeur),
        coins = 16
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BulleRonde(
                url = role.character.image,
                contentDescription = role.character.fullName,
                taille = TAILLE_BULLE.dp
            )

            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = role.character.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                roleTraduit?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MistGrey,
                        maxLines = 1
                    )
                }
            }

            if (voix != null) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = voix.fullName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.label_voice_japanese),
                        style = MaterialTheme.typography.labelSmall,
                        color = MistGrey,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }

                BulleRonde(
                    url = voix.image,
                    contentDescription = voix.fullName,
                    taille = TAILLE_BULLE.dp
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Membre du staff : bulle, nom et fonction. Volontairement distinct des
 * personnages, avec lesquels il ne doit pas etre confondu.
 */
@Composable
fun CarteStaff(
    staff: StaffRole,
    roleAffiche: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 160.dp
) {
    CarteFocalisable(
        onClick = onClick,
        modifier = modifier.width(largeur),
        coins = 16
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BulleRonde(
                url = staff.member.image,
                contentDescription = staff.member.fullName,
                taille = TAILLE_BULLE.dp
            )
            Text(
                text = staff.member.fullName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            roleAffiche?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MistGrey,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val TAILLE_BULLE = 62
