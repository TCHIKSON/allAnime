package com.allanime.tv.domain.model

/**
 * Fabrique d'entrees de catalogue pour les tests.
 *
 * Les rangees de l'accueil ne regardent que le slug, le titre, les genres et les
 * types : le reste est laisse a des valeurs neutres.
 */
internal fun catalogueDeTest(
    slug: String = "anime",
    titre: String = slug,
    genres: List<String> = emptyList(),
    types: List<String> = listOf("Anime"),
    langues: List<String> = listOf("VOSTFR"),
    image: String? = null,
    synopsis: String? = null,
    titresAlternatifs: List<String> = emptyList()
) = AnimeCatalogue(
    slug = slug,
    titre = titre,
    titresAlternatifs = titresAlternatifs,
    image = image,
    genres = genres,
    types = types,
    langues = langues,
    synopsis = synopsis
)
