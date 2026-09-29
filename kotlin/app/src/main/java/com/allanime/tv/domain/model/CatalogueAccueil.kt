package com.allanime.tv.domain.model

/**
 * Le catalogue du site, tel que liste par sa page `/catalogue` (accueil de
 * l'application).
 *
 * C'est desormais cette liste, et non plus les tendances AniList, qui alimente
 * l'accueil : elle a l'avantage de ne contenir que des animes reellement disponibles
 * a la lecture. Les fiches restent completees par AniList au moment ou l'utilisateur
 * ouvre un titre.
 */

/**
 * Une entree de la liste du catalogue.
 *
 * Tous les champs viennent de la carte HTML : ils sont donc partiels par nature
 * (pas de personnages, pas de studio, pas de note). Le [synopsis] et les [genres]
 * suffisent cependant a composer un accueil complet sans un seul appel AniList.
 *
 * @param slug identifiant du site, extrait du lien de la carte. C'est la clef qui
 *   permet ensuite d'aller chercher les saisons et les episodes, sans avoir a sonder
 *   plusieurs titres candidats comme le fait `SlugResolver`.
 */
data class AnimeCatalogue(
    val slug: String,
    val titre: String,
    val titresAlternatifs: List<String>,
    val image: String?,
    val genres: List<String>,
    val types: List<String>,
    val langues: List<String>,
    val synopsis: String?
) {

    /** `true` si le catalogue classe aussi ce titre parmi les films. */
    val estFilm: Boolean
        get() = types.any { it.trim().startsWith(TYPE_FILM, ignoreCase = true) }

    /**
     * Titres a essayer sur AniList, du plus probable au moins probable.
     *
     * Le titre affiche par le catalogue est parfois francise (« Le Voyage de
     * Chihiro »), alors qu'AniList indexe surtout le romaji et l'anglais : les titres
     * alternatifs de la carte, eux, contiennent presque toujours l'un des deux.
     */
    val titresPourRecherche: List<String>
        get() = (listOf(titre) + titresAlternatifs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TITRES_RECHERCHE)

    /**
     * Identifiant de repli, utilise quand AniList ne connait pas ce titre.
     *
     * Il est derive du slug, donc stable d'un lancement a l'autre : l'historique de
     * visionnage reste valable. Il est negatif pour ne jamais entrer en collision
     * avec un identifiant AniList, qui est toujours positif.
     */
    val idDeRepli: Int
        get() = -((slug.hashCode() and Int.MAX_VALUE).coerceAtLeast(1))

    private companion object {
        const val TYPE_FILM = "Film"

        /** Au-dela, on s'acharne sur un titre qu'AniList ne connait manifestement pas. */
        const val MAX_TITRES_RECHERCHE = 3
    }
}

/**
 * Une page de la liste du catalogue.
 *
 * Le site pagine sa liste ; [nombrePages] est lu dans le bloc de pagination et permet
 * de savoir combien de pages restent a telecharger.
 */
data class PageCatalogue(
    val animes: List<AnimeCatalogue>,
    val nombrePages: Int
)

/**
 * Fiche partielle construite a partir de la seule carte du catalogue.
 *
 * Elle sert de filet quand AniList ne connait pas le titre : la page de la serie
 * reste consultable et les episodes restent lisibles, seules les sections
 * enrichies — personnages, staff, notes, oeuvres liees — manquent a l'appel. Mieux
 * vaut une fiche sobre qu'un ecran d'erreur devant un anime qui, lui, est bien la.
 */
fun AnimeCatalogue.versAnime(): Anime = Anime(
    id = idDeRepli,
    idMal = null,
    title = AnimeTitle(romaji = titre, english = null, nativeTitle = null),
    synonyms = titresAlternatifs,
    description = synopsis,
    format = types.firstOrNull(),
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
    genres = genres,
    tags = emptyList(),
    bannerImage = null,
    coverImage = CoverImage(extraLarge = image, large = image, medium = image, color = null),
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
