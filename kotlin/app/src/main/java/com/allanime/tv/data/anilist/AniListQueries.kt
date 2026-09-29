package com.allanime.tv.data.anilist

/**
 * Requetes GraphQL AniList (section 4.3 du cahier des charges).
 *
 * Les deux requetes partagent exactement le meme jeu de champs [MEDIA_FIELDS] : une
 * fiche ouverte depuis la recherche et une fiche rechargee par identifiant doivent
 * afficher la meme chose. Elles sont donc construites ici plutot que recopiees, pour
 * eviter qu'un champ ajoute d'un cote soit oublie de l'autre.
 */
internal object AniListQueries {

    private const val MEDIA_FIELDS = "id idMal title { romaji english native } synonyms " +
        "description(asHtml: false) format status episodes duration season seasonYear source " +
        "hashtag countryOfOrigin isAdult averageScore meanScore popularity favourites trending " +
        "genres tags { id name description rank isMediaSpoiler } bannerImage " +
        "coverImage { extraLarge large medium color } startDate { year month day } " +
        "endDate { year month day } " +
        "studios { edges { isMain node { id name isAnimationStudio siteUrl } } } " +
        "characters(sort: ROLE, perPage: 100) { edges { role " +
        "voiceActors(language: JAPANESE, sort: RELEVANCE) { id name { full native } " +
        "image { large } } node { id name { full native } image { large } gender age " +
        "description } } } " +
        "staff(perPage: 100) { edges { role node { id name { full native } image { large } " +
        "description } } } " +
        "rankings { rank type format year season allTime context } " +
        "recommendations(sort: RATING_DESC, perPage: 25) { nodes { rating " +
        "mediaRecommendation { id title { romaji english } coverImage { large } } } } " +
        "externalLinks { id url site type language color icon } " +
        "streamingEpisodes { title thumbnail url site } " +
        "trailer { id site thumbnail } siteUrl updatedAt"

    /** Recherche par titre ; la valeur est passee via la variable `search`. */
    fun search(perPage: Int): String =
        "query (${'$'}search: String) { Page(perPage: $perPage) " +
            "{ media(search: ${'$'}search, type: ANIME) { $MEDIA_FIELDS } } }"

    /**
     * Fiche unique par identifiant. Utilisee quand l'application revient sur une fiche
     * dont le cache memoire a ete vide (processus recycle par Android TV).
     */
    fun byId(): String =
        "query (${'$'}id: Int) { Media(id: ${'$'}id, type: ANIME) { $MEDIA_FIELDS } }"
}
