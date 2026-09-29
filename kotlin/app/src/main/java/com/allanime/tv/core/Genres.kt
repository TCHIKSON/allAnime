package com.allanime.tv.core

/**
 * Traduction des genres AniList.
 *
 * AniList n'emploie qu'un ensemble ferme de genres, tous en anglais. Comme ils
 * servent de titres de rangees sur l'accueil, ils sont traduits ici une fois pour
 * toutes. Un genre inconnu — ajout futur cote AniList — est laisse tel quel plutot
 * que remplace par une approximation.
 */
object Genres {

    private val TRADUCTIONS = mapOf(
        "Action" to "Action",
        "Adventure" to "Aventure",
        "Comedy" to "Comédie",
        "Drama" to "Drame",
        "Ecchi" to "Ecchi",
        "Fantasy" to "Fantasy",
        "Hentai" to "Hentai",
        "Horror" to "Horreur",
        "Mahou Shoujo" to "Magical girl",
        "Mecha" to "Mecha",
        "Music" to "Musique",
        "Mystery" to "Mystère",
        "Psychological" to "Psychologique",
        "Romance" to "Romance",
        "Sci-Fi" to "Science-fiction",
        "Slice of Life" to "Tranche de vie",
        "Sports" to "Sport",
        "Supernatural" to "Surnaturel",
        "Thriller" to "Thriller"
    )

    fun traduire(genre: String): String {
        val propre = genre.trim()
        return TRADUCTIONS[propre] ?: propre
    }
}
