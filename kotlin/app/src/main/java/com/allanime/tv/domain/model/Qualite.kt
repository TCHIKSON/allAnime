package com.allanime.tv.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Qualites video proposees par la page du lecteur courant.
 *
 * Ces modeles sont remplis a partir de ce que la page expose reellement — niveaux
 * HLS, balises `<source>` ou menu d'un lecteur tiers. Rien n'est extrapole : quand
 * la page ne permet pas de choisir, l'inventaire ne contient que l'entree
 * automatique et l'interface le dit au lieu d'afficher de fausses resolutions.
 *
 * Les annotations de serialisation correspondent aux clefs produites par le script
 * injecte ([com.allanime.tv.player.OverlayScripts]).
 */
@Serializable
data class QualiteVideo(
    val id: String,
    /** Hauteur en pixels, `0` si la page ne l'indique pas. */
    val hauteur: Int = 0,
    /** Libelle brut de la page, quand il n'est pas exprime en pixels (« HD »...). */
    val libelle: String? = null
) {
    /**
     * Certaines pages declarent elles-memes un niveau « Auto » parmi leurs qualites.
     * Le reconnaitre evite de le proposer une seconde fois a cote de notre propre
     * entree automatique.
     */
    val estAutomatique: Boolean
        get() = id == ID_AUTO || libelle?.trim()?.equals(ID_AUTO, ignoreCase = true) == true

    companion object {
        /** Laisse le lecteur adapter la qualite au debit disponible. */
        const val ID_AUTO = "auto"
    }
}

@Serializable
data class InventaireQualites(
    /** Identifiant de l'entree active, `auto` par defaut. */
    val actif: String = QualiteVideo.ID_AUTO,
    val entrees: List<QualiteVideo> = emptyList(),
    /** Hauteur reellement affichee, `0` si la page ne la donne pas. */
    @SerialName("hauteur")
    val hauteurCourante: Int = 0
) {
    /** `true` quand la page n'expose aucune resolution selectionnable. */
    val estFigee: Boolean get() = entrees.none { !it.estAutomatique }

    /**
     * Entrees a presenter, de la plus fine a la plus grossiere, l'automatique en
     * tete. Les doublons de hauteur sont ecartes : certains lecteurs declarent deux
     * fois la meme resolution avec des debits differents, ce qui n'a aucun sens a
     * afficher sur un televiseur.
     */
    val entreesTriees: List<QualiteVideo>
        get() {
            if (estFigee) return emptyList()

            val choisissables = entrees
                .filterNot { it.estAutomatique }
                .distinctBy { if (it.hauteur > 0) it.hauteur.toString() else it.id }
                .sortedByDescending { it.hauteur }

            return listOf(QualiteVideo(QualiteVideo.ID_AUTO)) + choisissables
        }

    companion object {
        val VIDE = InventaireQualites()
    }
}
