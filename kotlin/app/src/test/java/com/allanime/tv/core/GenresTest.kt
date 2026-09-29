package com.allanime.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Traduction des genres AniList. Ils servent de titres de rangees sur l'accueil : un
 * genre non traduit doit rester lisible, pas devenir une approximation.
 */
class GenresTest {

    @Test
    fun `les genres courants sont traduits`() {
        assertEquals("Aventure", Genres.traduire("Adventure"))
        assertEquals("Comédie", Genres.traduire("Comedy"))
        assertEquals("Tranche de vie", Genres.traduire("Slice of Life"))
        assertEquals("Science-fiction", Genres.traduire("Sci-Fi"))
        assertEquals("Surnaturel", Genres.traduire("Supernatural"))
    }

    @Test
    fun `les genres identiques en francais sont rendus tels quels`() {
        assertEquals("Action", Genres.traduire("Action"))
        assertEquals("Romance", Genres.traduire("Romance"))
        assertEquals("Thriller", Genres.traduire("Thriller"))
    }

    @Test
    fun `un genre inconnu est laisse intact`() {
        assertEquals("Isekai", Genres.traduire("Isekai"))
    }

    @Test
    fun `les espaces autour du genre sont ignores`() {
        assertEquals("Mystère", Genres.traduire("  Mystery  "))
    }
}
