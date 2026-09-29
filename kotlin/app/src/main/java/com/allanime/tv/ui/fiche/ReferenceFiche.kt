package com.allanime.tv.ui.fiche

/**
 * Ce qui designe la serie a afficher, et d'ou viennent ses saisons et ses episodes.
 *
 * Les saisons et les episodes viennent toujours du **catalogue scrape** ; seule
 * change la facon dont on y entre :
 *
 *  - [ParId] part d'un identifiant AniList — c'est le cas de la recherche et des
 *    recommandations d'une fiche ;
 *  - [ParSlug] part d'un slug du catalogue — c'est le cas de l'accueil et du
 *    planning, dont les cartes le portent deja : le resolveur n'a rien a sonder.
 *
 * Dans les deux cas, la fiche elle-meme — synopsis, distribution, notes — vient
 * d'AniList.
 */
sealed interface ReferenceFiche {

    /** Clef stable, qui distingue deux fiches dans le cache des ViewModels. */
    val cle: String

    data class ParId(val animeId: Int) : ReferenceFiche {
        override val cle: String get() = "id-$animeId"
    }

    data class ParSlug(val slug: String) : ReferenceFiche {
        override val cle: String get() = "slug-$slug"
    }
}
