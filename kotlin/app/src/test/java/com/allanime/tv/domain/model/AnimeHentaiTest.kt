package com.allanime.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un anime de genre Hentai est reconnu, pour etre retire des reponses d'AniList. */
class AnimeHentaiTest {

    @Test
    fun `le genre Hentai est reconnu quelle que soit sa casse`() {
        assertTrue(animeDeTest(genres = listOf("Comedy", "Hentai")).estHentai)
        assertTrue(animeDeTest(genres = listOf("hentai")).estHentai)
    }

    @Test
    fun `les autres genres ne sont pas concernes`() {
        assertFalse(animeDeTest(genres = listOf("Ecchi", "Romance")).estHentai)
        assertFalse(animeDeTest(genres = emptyList()).estHentai)
    }
}
