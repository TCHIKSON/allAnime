package com.allanime.tv.ui.navigation

/**
 * Routes de navigation.
 *
 * L'empilement suit la logique demandee par la section 26 :
 * accueil -> fiche -> episodes -> lecteur, le bouton Retour depilant dans l'ordre
 * inverse.
 *
 * La page d'une serie a deux portes d'entree, selon l'identifiant dont on dispose :
 * [FICHE] pour un identifiant AniList (recherche, recommandations) et
 * [FICHE_CATALOGUE] pour un slug du catalogue (accueil, planning). Elles ouvrent le
 * meme ecran ; voir `ReferenceFiche`.
 */
object Routes {

    const val ACCUEIL = "accueil"
    const val RECHERCHE = "recherche"
    const val PLANNING = "planning"

    /** Catalogue restreint, desservi par la barre laterale. */
    const val SERIES = "series"
    const val FILMS = "films"

    const val ARG_ANIME_ID = "animeId"
    const val ARG_SLUG = "slug"
    const val ARG_SAISON = "indexSaison"
    const val ARG_LANGUE = "codeLangue"
    const val ARG_EPISODE = "numeroEpisode"

    const val FICHE = "fiche/{$ARG_ANIME_ID}"
    const val FICHE_CATALOGUE = "fiche-catalogue/{$ARG_SLUG}"
    const val LECTEUR = "lecteur/{$ARG_ANIME_ID}/{$ARG_SAISON}/{$ARG_LANGUE}/{$ARG_EPISODE}"

    /**
     * Lecteur ouvert sur une URL directe (bande-annonce AniList).
     *
     * Le titre voyage en parametre de requete plutot qu'en segment de chemin : il
     * est facultatif, et un segment vide ne correspondrait a aucune route.
     */
    const val ARG_URL = "url"
    const val ARG_TITRE = "titre"
    const val LECTEUR_DIRECT = "lecteur-direct/{$ARG_URL}?$ARG_TITRE={$ARG_TITRE}"

    fun fiche(animeId: Int) = "fiche/$animeId"

    /** @param slug deja encode par l'appelant, le site n'ayant rien promis sur sa forme. */
    fun ficheCatalogue(slug: String) = "fiche-catalogue/$slug"

    fun lecteur(animeId: Int, indexSaison: Int, codeLangue: String, numeroEpisode: Int) =
        "lecteur/$animeId/$indexSaison/$codeLangue/$numeroEpisode"

    /** @param urlEncodee et [titreEncode] deja encodes par l'appelant. */
    fun lecteurDirect(urlEncodee: String, titreEncode: String) =
        "lecteur-direct/$urlEncodee?$ARG_TITRE=$titreEncode"
}
