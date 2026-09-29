package com.allanime.tv.domain.model

/**
 * Fabrique d'`Anime` pour les tests.
 *
 * Le modele suit fidelement la reponse d'AniList, donc il compte beaucoup de champs
 * nullables. Les tests n'en fixent que deux ou trois a la fois : cette fabrique evite
 * de recopier trente valeurs par cas.
 */
internal fun animeDeTest(
    id: Int = 1,
    romaji: String? = "Titre",
    english: String? = null,
    genres: List<String> = emptyList(),
    popularity: Int? = null,
    streamingEpisodes: List<StreamingEpisode> = emptyList()
) = Anime(
    id = id,
    idMal = null,
    title = AnimeTitle(romaji = romaji, english = english, nativeTitle = null),
    synonyms = emptyList(),
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
    popularity = popularity,
    favourites = null,
    trending = null,
    genres = genres,
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
    streamingEpisodes = streamingEpisodes,
    trailer = null,
    siteUrl = null,
    updatedAt = null
)

