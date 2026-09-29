package com.allanime.tv.domain.model

import com.allanime.tv.core.Slugifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie l'ordre des titres candidats servant a retrouver un anime dans le
 * catalogue : romaji d'abord, anglais en repli, puis synonymes et formes courtes.
 */
class CandidatsCatalogueTest {

    private fun anime(
        romaji: String? = null,
        english: String? = null,
        native: String? = null,
        synonyms: List<String> = emptyList()
    ) = Anime(
        id = 1,
        idMal = null,
        title = AnimeTitle(romaji = romaji, english = english, nativeTitle = native),
        synonyms = synonyms,
        description = null,
        format = null,
        status = null,
        episodes = null,
        duration = null,
        season = null,
        seasonYear = null,
        source = null,
        hashtag = null,
        countryOfOrigin = null,
        isAdult = false,
        averageScore = null,
        meanScore = null,
        popularity = null,
        favourites = null,
        trending = null,
        genres = emptyList(),
        tags = emptyList(),
        bannerImage = null,
        coverImage = CoverImage(null, null, null, null),
        startDate = null,
        endDate = null,
        studios = emptyList(),
        characters = emptyList(),
        staff = emptyList(),
            rankings = emptyList(),
        recommendations = emptyList(),
        externalLinks = emptyList(),
        streamingEpisodes = emptyList(),
        trailer = null,
        siteUrl = null,
        updatedAt = null
    )

    @Test
    fun `le romaji passe avant l anglais`() {
        val candidats = anime(
            romaji = "Shingeki no Kyojin",
            english = "Attack on Titan"
        ).candidatsCatalogue

        assertEquals("Shingeki no Kyojin", candidats.first())
        assertTrue(candidats.contains("Attack on Titan"))
    }

    @Test
    fun `le titre principal reste le romaji`() {
        val fiche = anime(romaji = "Sousou no Frieren", english = "Frieren: Beyond Journey's End")

        assertEquals("Sousou no Frieren", fiche.catalogueTitle)
    }

    @Test
    fun `l anglais sert de repli quand le romaji manque`() {
        val fiche = anime(english = "Classroom of the Elite")

        assertEquals("Classroom of the Elite", fiche.catalogueTitle)
        assertEquals("Classroom of the Elite", fiche.candidatsCatalogue.first())
    }

    @Test
    fun `la forme courte avant deux points est proposee`() {
        // Le catalogue publie « mushoku-tensei », pas le titre complet.
        val candidats = anime(
            romaji = "Mushoku Tensei: Isekai Ittara Honki Dasu",
            english = "Mushoku Tensei: Jobless Reincarnation"
        ).candidatsCatalogue

        assertTrue(candidats.contains("Mushoku Tensei"))
        assertTrue(
            candidats.map(Slugifier::slugify).contains("mushoku-tensei")
        )
    }

    @Test
    fun `la forme courte couvre aussi demon slayer`() {
        val slugs = anime(
            romaji = "Kimetsu no Yaiba",
            english = "Demon Slayer: Kimetsu no Yaiba"
        ).candidatsCatalogue.map(Slugifier::slugify)

        assertTrue(slugs.contains("demon-slayer"))
    }

    @Test
    fun `les synonymes sont proposes apres les titres principaux`() {
        val candidats = anime(
            romaji = "Yomi no Tsugai",
            english = "Daemons of the Shadow Realm",
            synonyms = listOf("YomiTsuga")
        ).candidatsCatalogue

        assertEquals("Yomi no Tsugai", candidats.first())
        assertTrue(candidats.indexOf("YomiTsuga") > candidats.indexOf("Daemons of the Shadow Realm"))
    }

    @Test
    fun `aucun doublon ni valeur vide`() {
        val candidats = anime(
            romaji = "One Piece",
            english = "One Piece",
            synonyms = listOf("", "   ")
        ).candidatsCatalogue

        assertEquals(listOf("One Piece"), candidats)
    }
}
