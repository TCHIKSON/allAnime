package com.allanime.tv.data.repository

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.scraper.EpisodesScraper
import com.allanime.tv.data.scraper.SaisonsScraper
import com.allanime.tv.data.scraper.SlugResolver
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.Saison
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saisons et episodes du catalogue (sections 9 a 12 du cahier des charges).
 *
 * C'est le seul endroit qui sait transformer un anime en URL : les titres candidats
 * sont slugifies puis concatenes a la racine du catalogue, lue depuis le secret
 * Infisical `BASE_URL_2A` via [AppConfig]. Le slug retenu est celui dont la fiche
 * existe reellement, determine une fois par [SlugResolver] puis memorise.
 *
 * Les resultats sont mis en cache par slug et par chemin de langue : revenir sur une
 * fiche ou changer d'episode ne relance pas de scraping.
 */
class CatalogueRepository(
    private val saisonsScraper: SaisonsScraper,
    private val episodesScraper: EpisodesScraper,
    private val slugResolver: SlugResolver
) {

    private val slugParAnime = ConcurrentHashMap<Int, String>()
    private val saisonsParSlug = ConcurrentHashMap<String, List<Saison>>()
    private val episodesParChemin = ConcurrentHashMap<String, List<Episode>>()
    private val verrou = Mutex()

    /**
     * Declare le slug d'un anime, sans le faire resoudre.
     *
     * Utilise quand on vient de la liste du catalogue : le slug y figure deja, il
     * serait absurde de sonder des titres candidats pour retrouver une page dont on
     * connait l'adresse exacte.
     */
    fun associer(animeId: Int, slug: String) {
        slugParAnime[animeId] = slug
    }

    /**
     * Slug du catalogue pour cet anime, resolu au premier appel puis memorise.
     *
     * @throws DataException.CatalogueUnavailable si aucun titre candidat ne
     *   correspond a une fiche existante.
     */
    suspend fun slug(anime: Anime): String {
        slugParAnime[anime.id]?.let { return it }

        return verrou.withLock {
            slugParAnime[anime.id]?.let { return@withLock it }

            val candidats = anime.candidatsCatalogue
            val resolu = slugResolver.resoudre(candidats)
                ?: throw DataException.CatalogueUnavailable(
                    "Aucune fiche catalogue pour « ${anime.displayTitle} » " +
                        "(essais : ${candidats.joinToString()})"
                )

            slugParAnime[anime.id] = resolu
            resolu
        }
    }

    /** URL de la fiche catalogue de cet anime. */
    suspend fun urlCatalogue(anime: Anime): String = AppConfig.catalogueUrl(slug(anime))

    /** Saisons disponibles pour un anime. */
    /**
     * Saisons du catalogue, telles que [SaisonsScraper] les a retenues.
     *
     * C'est lui qui ecarte les saisons fantomes — sans langue, sans episode
     * exploitable, ou recopiant une saison precedente. Le tri est fait **la et nulle
     * part ailleurs** : la fiche et le lecteur designent une saison par sa position
     * dans cette liste, et filtrer d'un cote seulement les ferait pointer sur deux
     * saisons differentes.
     */
    suspend fun saisons(anime: Anime, rafraichir: Boolean = false): List<Saison> {
        val slug = slug(anime)

        if (!rafraichir) saisonsParSlug[slug]?.let { return it }

        return verrou.withLock {
            if (!rafraichir) saisonsParSlug[slug]?.let { return@withLock it }

            val resultat = saisonsScraper.fetch(AppConfig.catalogueUrl(slug))
            saisonsParSlug[slug] = resultat
            resultat
        }
    }

    /**
     * Langues d'une saison qui donnent reellement une version differente.
     *
     * **Le site repond 200 pour un segment de langue qui n'existe pas**, et sert
     * alors la version originale. `SaisonsScraper` ne peut pas le voir : il ne fait
     * que sonder l'existence de la page. Resultat, une saison annonce « VOSTFR, VF »
     * alors que les deux adresses rendent exactement les memes lecteurs — l'interface
     * promet une VF et lit de la VOSTFR.
     *
     * Le seul moyen de trancher est de comparer ce que chaque langue livre vraiment.
     * Deux langues aux memes lecteurs ne sont qu'une seule et meme version publiee
     * sous deux adresses : on ne garde que la premiere, l'ordre de [LangueCode]
     * placant la version originale devant.
     *
     * Le cout est nul en pratique : les listes obtenues sont mises en cache par
     * chemin, et ce sont celles que l'ecran affichera juste apres.
     *
     * Une langue dont la lecture echoue est **conservee** : on ne retire que ce dont
     * on a la preuve que c'est un doublon.
     */
    suspend fun languesDistinctes(anime: Anime, saison: Saison): List<Langue> =
        coroutineScope {
            if (saison.langues.size <= 1) return@coroutineScope saison.langues

            val empreintes = saison.langues.map { langue ->
                async {
                    langue to runCatchingCancellable { episodes(anime, langue) }
                        .getOrNull()
                        ?.map { episode -> episode.lecteurs.map { it.url } }
                }
            }.awaitAll()

            val vues = mutableSetOf<List<List<String>>>()
            empreintes.mapNotNull { (langue, empreinte) ->
                when {
                    // Illisible : on ne peut rien prouver, on la garde.
                    empreinte == null -> langue
                    // Vide : la page existe mais ne propose aucun episode.
                    empreinte.isEmpty() -> null
                    vues.add(empreinte) -> langue
                    else -> null
                }
            }
        }

    /**
     * Episodes d'une saison dans une langue donnee.
     *
     * Les episodes ne sont jamais melanges entre saisons ou entre langues : la clef
     * de cache est l'URL complete `.../saisonN/<langue>`.
     */
    suspend fun episodes(
        anime: Anime,
        langue: Langue,
        rafraichir: Boolean = false
    ): List<Episode> {
        val url = urlLangue(anime, langue)

        if (!rafraichir) episodesParChemin[url]?.let { return it }

        return verrou.withLock {
            if (!rafraichir) episodesParChemin[url]?.let { return@withLock it }

            val resultat = episodesScraper.fetch(url)
            episodesParChemin[url] = resultat
            resultat
        }
    }

    /** URL absolue d'une saison dans une langue : `<base>/<slug>/saison1/vostfr`. */
    suspend fun urlLangue(anime: Anime, langue: Langue): String =
        URI(urlCatalogue(anime)).resolve(langue.path).toString().trimEnd('/')
}
