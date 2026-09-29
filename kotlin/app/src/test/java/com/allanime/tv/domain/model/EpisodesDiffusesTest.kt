package com.allanime.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vignettes d'episodes AniList : le numero doit etre lu dans un titre libre, et un
 * titre illisible doit etre ignore plutot que numerote au hasard.
 */
class EpisodesDiffusesTest {

    private fun episode(titre: String?, vignette: String? = "https://img/x.jpg") =
        StreamingEpisode(title = titre, thumbnail = vignette, url = null, site = "Crunchyroll")

    @Test
    fun `le numero est lu dans le titre complet`() {
        val diffuses = animeDeTest(
            streamingEpisodes = listOf(
                episode("Episode 1 - To You, 2000 Years From Now"),
                episode("Episode 2 - That Day")
            )
        ).episodesDiffuses

        assertEquals(setOf(1, 2), diffuses.keys)
        assertEquals("https://img/x.jpg", diffuses[1]?.thumbnail)
    }

    @Test
    fun `les abreviations sont reconnues`() {
        val diffuses = animeDeTest(
            streamingEpisodes = listOf(episode("Ep. 12 - Fin"), episode("ep3 - Debut"))
        ).episodesDiffuses

        assertEquals(setOf(12, 3), diffuses.keys)
    }

    @Test
    fun `un titre sans numero est ignore`() {
        val diffuses = animeDeTest(
            streamingEpisodes = listOf(episode("Bande-annonce"), episode(null))
        ).episodesDiffuses

        assertTrue(diffuses.isEmpty())
    }

    @Test
    fun `le prefixe de numerotation est retire du titre affiche`() {
        assertEquals("That Day", episode("Episode 2 - That Day").titreCourt)
        assertEquals("Fin", episode("Ep. 12 : Fin").titreCourt)
    }

    @Test
    fun `un titre reduit a son prefixe ne laisse rien a afficher`() {
        assertNull(episode("Episode 5").titreCourt)
    }

    @Test
    fun `un anime sans episodes diffuses donne une table vide`() {
        assertTrue(animeDeTest().episodesDiffuses.isEmpty())
    }
}
