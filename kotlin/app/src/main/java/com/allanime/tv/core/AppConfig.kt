package com.allanime.tv.core

import com.allanime.tv.BuildConfig

/**
 * Point d'acces unique a la configuration de l'application (section 31 du cahier
 * des charges).
 *
 * Les valeurs sont injectees dans [BuildConfig] au moment du build par
 * `app/build.gradle.kts` : [baseUrl] vient du secret Infisical `BASE_URL_2A`, les
 * autres du fichier `kotlin/.env`. Aucun ecran, ViewModel ou scraper ne doit contenir
 * d'URL codee en dur : tout passe par cet objet.
 */
object AppConfig {

    /**
     * Racine du catalogue, sans slash final. Exemple : `https://exemple.tld/catalogue`.
     * Provient du secret Infisical `BASE_URL_2A`.
     */
    val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/')

    /**
     * Racine du site de lecture, sans slash final. Provient du secret Infisical
     * `BASE_URL_LIS`.
     *
     * Ce n'est pas le meme site que [baseUrl]. C'est une seconde source de saisons,
     * d'episodes et de lecteurs, interrogee en plus du catalogue et jamais a sa place
     * (voir `LisScraper` et `CatalogueRepository`).
     */
    val lisBaseUrl: String = BuildConfig.BASE_URL_LIS.trimEnd('/')

    /** Endpoint GraphQL AniList. */
    val aniListEndpoint: String = BuildConfig.ANILIST_ENDPOINT

    /** User-Agent applique au WebView qui affiche les lecteurs (section 19). */
    val playerUserAgent: String = BuildConfig.PLAYER_USER_AGENT

    /** User-Agent applique aux requetes de scraping du catalogue. */
    val scraperUserAgent: String = BuildConfig.SCRAPER_USER_AGENT

    /**
     * Dernier segment de [baseUrl], c'est-a-dire `catalogue`.
     *
     * Les liens presents dans les pages du site sont parfois absolus et pointent vers
     * un autre nom de domaine que celui configure ici. Ce segment sert donc de repere
     * pour retrouver le slug dans un lien, sans jamais supposer le nom d'hote
     * (voir `LienCatalogue`).
     */
    val segmentCatalogue: String = baseUrl.substringAfterLast('/')

    /**
     * Liste complete des animes du catalogue, filtres sur le type « Anime ».
     *
     * C'est la page qui alimente l'accueil : elle renvoie du HTML, transforme en
     * modeles par `CatalogueListeScraper`. Les bornes de la requete sont laissees
     * vides volontairement — le site attend les parametres, meme sans valeur.
     */
    val catalogueListeUrl: String = "$baseUrl/?type%5B%5D=Anime&annee_min=&annee_max=" +
        "&episodes_min=&episodes_max=&chapitres_min=&chapitres_max=&search="

    /**
     * Planning hebdomadaire des sorties.
     *
     * Meme racine que le catalogue, le dernier segment `/catalogue` etant remplace par
     * `/planning` : `https://exemple.tld/catalogue` donne `https://exemple.tld/planning`.
     */
    val planningUrl: String = baseUrl.substringBeforeLast('/') + "/planning"

    /**
     * Point d'entree du moteur de recherche du site, obtenu de la meme facon :
     * `/catalogue` cede la place au script qui repond aux requetes `POST`.
     */
    val rechercheUrl: String =
        baseUrl.substringBeforeLast('/') + "/template-php/defaut/fetch.php"

    /**
     * Construit l'URL de la fiche catalogue d'un anime a partir de son slug.
     *
     * `classroom-of-the-elite` donne `https://exemple.tld/catalogue/classroom-of-the-elite/`.
     */
    fun catalogueUrl(slug: String): String = "$baseUrl/$slug/"

    /**
     * Page `numero` de la liste du catalogue. La page 1 est [catalogueListeUrl] tel
     * quel : le site la sert aussi bien avec que sans `page=1`, mais on evite une URL
     * differente pour le meme contenu.
     */
    fun catalogueListeUrl(numero: Int): String =
        if (numero <= 1) catalogueListeUrl else "$catalogueListeUrl&page=$numero"
}
