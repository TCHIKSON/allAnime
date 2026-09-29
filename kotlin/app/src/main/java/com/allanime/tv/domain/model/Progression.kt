package com.allanime.tv.domain.model

import kotlinx.serialization.Serializable

/**
 * Avancement de visionnage d'un anime, conserve sur le televiseur.
 *
 * Il n'y a pas de compte utilisateur : l'appareil est le contexte, et l'historique
 * ne quitte jamais la TV.
 *
 * Le modele porte directement l'annotation de serialisation, contrairement aux
 * modeles AniList qui passent par des DTO : ici le format stocke est notre propre
 * format, pas celui d'une API tierce, donc dupliquer la classe n'apporterait rien.
 *
 * Une seule entree est conservee par anime : celle de l'episode le plus recemment
 * regarde.
 */
@Serializable
data class ProgressionAnime(
    /** Identifiant AniList de la serie. */
    val animeId: Int,
    val titre: String,
    /**
     * Slug du catalogue, quand il est connu. Il relie l'entree d'historique a la
     * liste du catalogue qui alimente l'accueil, ce que l'identifiant AniList ne
     * permet pas. Absent des entrees ecrites par les versions precedentes.
     */
    val slug: String? = null,
    val image: String? = null,
    /**
     * Episodes reellement ouverts, par index de saison.
     *
     * L'entree d'historique ne retient qu'un seul episode — le dernier regarde — ce
     * qui suffit a reprendre la lecture mais pas a savoir ce qui a deja ete vu.
     * Sauter a l'episode 10 ne veut pas dire avoir vu les neuf precedents, et c'est
     * pourtant ce que le rang seul laissait croire.
     *
     * Absent des entrees ecrites par les versions precedentes : [aEteOuvert] retombe
     * alors sur l'episode courant, pour ne pas perdre ce qu'elles savaient.
     */
    val episodesOuverts: Map<Int, Set<Int>> = emptyMap(),
    val indexSaison: Int,
    val nomSaison: String,
    val codeLangue: String,
    val numeroEpisode: Int,
    val totalEpisodes: Int = 0,
    val positionSecondes: Int = 0,
    val dureeSecondes: Int = 0,
    val majEpochSeconde: Long = 0L
) {

    /**
     * `true` si cet episode precis a deja ete ouvert.
     *
     * Le repli sur l'episode courant couvre les entrees ecrites avant que la liste
     * n'existe : elles ne connaissent qu'un episode, mais elles le connaissent.
     */
    fun aEteOuvert(indexSaison: Int, numeroEpisode: Int): Boolean =
        episodesOuverts[indexSaison]?.contains(numeroEpisode) == true ||
            (this.indexSaison == indexSaison && this.numeroEpisode == numeroEpisode)

    /** La meme liste, augmentee de l'episode qu'on vient d'ouvrir. */
    fun avecEpisodeOuvert(indexSaison: Int, numeroEpisode: Int): Map<Int, Set<Int>> =
        episodesOuverts + (indexSaison to (episodesOuverts[indexSaison].orEmpty() + numeroEpisode))

    /** Avancement dans l'episode, entre 0 et 1. Vaut 0 si la duree est inconnue. */
    val avancement: Float
        get() = if (dureeSecondes <= 0) {
            0f
        } else {
            (positionSecondes.toFloat() / dureeSecondes).coerceIn(0f, 1f)
        }

    /**
     * `true` si l'episode est considere comme vu : les generiques de fin occupent
     * souvent la derniere minute, on ne demande donc pas 100 %.
     */
    val estTermine: Boolean
        get() = dureeSecondes > 0 && avancement >= SEUIL_TERMINE

    /** Libelle court affiche sur la carte de reprise : `S1 E3`. */
    val reperage: String
        get() = "S${indexSaison + 1} E$numeroEpisode"

    companion object {
        const val SEUIL_TERMINE = 0.92f
    }
}
