package com.allanime.tv.data.repository

import android.util.Log
import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.core.Slugifier
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.scraper.EpisodesScraper
import com.allanime.tv.data.scraper.LisScraper
import com.allanime.tv.data.scraper.SaisonsScraper
import com.allanime.tv.data.scraper.SlugResolver
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.Episode
import com.allanime.tv.domain.model.Langue
import com.allanime.tv.domain.model.LangueCode
import com.allanime.tv.domain.model.Lecteur
import com.allanime.tv.domain.model.RechercheLis
import com.allanime.tv.domain.model.Saison
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saisons et episodes d'un anime (sections 9 a 12 du cahier des charges).
 *
 * **Deux methodes cohabitent, et aucune ne remplace l'autre** :
 *
 *  - **le catalogue** `BASE_URL_2A`, la methode d'origine : les titres candidats sont
 *    slugifies, le slug retenu est celui dont la fiche existe ([SlugResolver]), puis
 *    [SaisonsScraper] et [EpisodesScraper] lisent les saisons et les episodes ;
 *  - **le site de lecture** `BASE_URL_LIS`, la methode ajoutee : [LisScraper] y
 *    retrouve la fiche par son titre, puis lit ses groupes, leurs episodes et leurs
 *    lecteurs. C'est le port du script de reference `scripts/lis.js`.
 *
 * Les deux sont lancees ensemble a l'ouverture d'une serie. Les saisons du catalogue
 * viennent en premier, a leur place habituelle ; celles que seul le site de lecture
 * connait sont ajoutees a la suite. Quand le catalogue n'a rien pour une serie, le
 * site de lecture suffit a la rendre lisible — et inversement.
 *
 * Les resultats sont mis en cache : revenir sur une fiche ou changer d'episode ne
 * relance aucune lecture.
 */
class CatalogueRepository(
    private val saisonsScraper: SaisonsScraper,
    private val episodesScraper: EpisodesScraper,
    private val slugResolver: SlugResolver,
    private val lisScraper: LisScraper
) {

    private val slugParAnime = ConcurrentHashMap<Int, String>()
    private val saisonsParSlug = ConcurrentHashMap<String, List<Saison>>()
    private val episodesParChemin = ConcurrentHashMap<String, List<Episode>>()
    private val verrou = Mutex()

    /** Titres de la fiche detaillee, declares par l'ecran qui l'a ouverte. */
    private val titresParAnime = ConcurrentHashMap<Int, List<String>>()
    private val saisonsParAnime = ConcurrentHashMap<Int, List<Saison>>()
    private val episodesLisParGroupe = ConcurrentHashMap<String, List<LisScraper.EpisodeListe>>()
    private val lecteursLisParEpisode = ConcurrentHashMap<String, Map<String, List<String>>>()
    private val verrouSaisons = Mutex()

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
     * Declare les titres sous lesquels la fiche detaillee presente un anime.
     *
     * Ils servent a la recherche sur le site de lecture, qui ne reconnait une serie
     * que sous le nom qu'il lui donne — le plus souvent celui de la carte du
     * catalogue, pas le romaji d'AniList.
     */
    fun declarerTitres(animeId: Int, titres: List<String>) {
        if (titres.isNotEmpty()) titresParAnime[animeId] = titres
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

    /**
     * Saisons de l'anime, catalogue puis site de lecture.
     *
     * La fiche et le lecteur designent une saison par sa position dans cette liste :
     * elle est etablie ici une fois, et servie telle quelle aux deux. Les saisons du
     * catalogue gardent leur rang d'avant l'ajout du site de lecture, l'historique
     * deja enregistre reste donc valable.
     *
     * @throws DataException.CatalogueUnavailable si aucune des deux methodes ne
     *   trouve la serie.
     */
    suspend fun saisons(anime: Anime, rafraichir: Boolean = false): List<Saison> {
        if (!rafraichir) saisonsParAnime[anime.id]?.let { return it }

        return verrouSaisons.withLock {
            if (!rafraichir) saisonsParAnime[anime.id]?.let { return@withLock it }

            val (catalogue, lecture) = coroutineScope {
                val ancienne = async {
                    runCatchingCancellable { saisonsCatalogue(anime, rafraichir) }
                }
                val ajoutee = async {
                    runCatchingCancellable {
                        withTimeoutOrNull(DELAI_LIS) { saisonsLis(anime) { ancienne.await() } }
                    }
                }
                ancienne.await() to ajoutee.await()
            }

            catalogue.onFailure { Log.w(TAG, "Catalogue sans saison : ${it.message}") }
            lecture.onFailure { Log.w(TAG, "Site de lecture sans saison : ${it.message}") }

            val duCatalogue = catalogue.getOrNull().orEmpty()
            val deLaLecture = lecture.getOrNull().orEmpty()
            Log.i(
                TAG,
                "« ${anime.displayTitle} » : ${duCatalogue.size} saison(s) du catalogue, " +
                    "${deLaLecture.size} du site de lecture"
            )

            // Une saison presente des deux cotes n'est montree qu'une fois, sous sa
            // forme d'origine : le site de lecture n'ajoute que ce qui manquait.
            val connues = duCatalogue.map { cle(it.nom) }.toSet()
            val resultat = duCatalogue + deLaLecture.filter { cle(it.nom) !in connues }

            // Rien nulle part : c'est l'echec du catalogue qui est rendu, comme
            // avant, pour que l'ecran dise ce qu'il a toujours dit.
            if (resultat.isEmpty()) catalogue.getOrThrow()

            saisonsParAnime[anime.id] = resultat
            resultat
        }
    }

    /**
     * Langues d'une saison qui donnent reellement une version differente.
     *
     * **Le catalogue repond 200 pour un segment de langue qui n'existe pas**, et sert
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
     * Une langue dont la lecture echoue est **conservee** : on ne retire que ce dont
     * on a la preuve que c'est un doublon.
     *
     * Sur le site de lecture, la question ne se pose pas ainsi : la page du groupe
     * dit, episode par episode, quelles langues existent.
     */
    suspend fun languesDistinctes(anime: Anime, saison: Saison): List<Langue> {
        if (saison.path.startsWith(PREFIXE_LIS)) {
            val presentes = episodesLis(saison.baseUrl).flatMap { it.langues }
            return languesLis(saison.path, presentes).ifEmpty { saison.langues }
        }

        return coroutineScope {
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
    }

    /**
     * Episodes d'une saison dans une langue donnee.
     *
     * Les episodes ne sont jamais melanges entre saisons ou entre langues. Ceux du
     * site de lecture sont rendus **sans leurs lecteurs**, que ce site ne declare que
     * sur la page de chaque episode : [lecteurs] les complete au lancement.
     */
    suspend fun episodes(
        anime: Anime,
        langue: Langue,
        rafraichir: Boolean = false
    ): List<Episode> {
        if (langue.path.startsWith(PREFIXE_LIS)) return episodesLis(anime, langue)

        val url = urlLangue(anime, langue)

        if (!rafraichir) episodesParChemin[url]?.let { return it }

        return verrou.withLock {
            if (!rafraichir) episodesParChemin[url]?.let { return@withLock it }

            val resultat = episodesScraper.fetch(url)
            episodesParChemin[url] = resultat
            resultat
        }
    }

    /**
     * Le meme episode, avec ses lecteurs dans la langue demandee.
     *
     * Un episode du catalogue les porte deja et revient tel quel. Pour un episode du
     * site de lecture, une seule lecture de sa page couvre toutes les langues.
     */
    suspend fun lecteurs(langue: Langue, episode: Episode): Episode {
        val url = episode.url ?: return episode
        if (episode.lecteurs.isNotEmpty()) return episode

        val parLangue = lecteursLisParEpisode[url]
            ?: lisScraper.lecteurs(url).also { lecteursLisParEpisode[url] = it }

        return episode.copy(lecteurs = versLecteurs(parLangue, langue))
    }

    /** URL absolue d'une saison dans une langue : `<base>/<slug>/saison1/vostfr`. */
    suspend fun urlLangue(anime: Anime, langue: Langue): String =
        URI(urlCatalogue(anime)).resolve(langue.path).toString().trimEnd('/')

    // --- Methode d'origine : le catalogue -------------------------------------

    /**
     * Saisons du catalogue, telles que [SaisonsScraper] les a retenues.
     *
     * C'est lui qui ecarte les saisons fantomes — sans langue, sans episode
     * exploitable, ou recopiant une saison precedente.
     */
    private suspend fun saisonsCatalogue(anime: Anime, rafraichir: Boolean): List<Saison> {
        val slug = slug(anime)

        if (!rafraichir) saisonsParSlug[slug]?.let { return it }

        return saisonsScraper.fetch(AppConfig.catalogueUrl(slug))
            .also { saisonsParSlug[slug] = it }
    }

    // --- Methode ajoutee : le site de lecture ---------------------------------

    /**
     * Saisons du site de lecture.
     *
     * @param apresCatalogue attend la fin de la methode d'origine. Elle n'est
     *   appelee que si les titres deja connus n'ont rien donne : le catalogue, une
     *   fois sa fiche trouvee, en livre d'autres.
     */
    private suspend fun saisonsLis(
        anime: Anime,
        apresCatalogue: suspend () -> Unit
    ): List<Saison> {
        val connus = titres(anime)
        val fiche = ficheLis(connus)
            ?: run {
                apresCatalogue()
                // Les titres sous lesquels le catalogue connait la serie : souvent
                // le nom francais, que ni la fiche AniList ni sa carte ne donnent.
                val autres = slugParAnime[anime.id]?.let(slugResolver::titres).orEmpty()
                ficheLis(autres - connus.toSet())
            }
            ?: throw DataException.CatalogueUnavailable(
                "Aucune fiche sur le site de lecture pour « ${anime.displayTitle} »"
            )

        val groupes = lisScraper.groupes(fiche)
        return coroutineScope { groupes.map { async { saisonLis(it) } }.awaitAll() }
            .filter { it.langues.isNotEmpty() && it.nombreEpisodes != 0 }
            .ifEmpty { listOfNotNull(filmLis(fiche)) }
    }

    /**
     * Titres de la fiche detaillee, du plus sur au moins sur.
     *
     * Ceux de la carte du catalogue passent devant : les deux sites nomment les
     * series de la meme facon, alors qu'AniList les connait d'abord en romaji.
     */
    private fun titres(anime: Anime): List<String> =
        // Jamais les formes raccourcies de `candidatsCatalogue` : `RechercheLis`
        // derive lui-meme la partie courte d'un titre, et ne l'accepte qu'a l'identique.
        (titresParAnime[anime.id].orEmpty() + anime.displayTitle + anime.titresComplets)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Adresse de la fiche sur le site de lecture, ou `null` si aucun titre n'y mene.
     *
     * Le moteur du site ne trouve une serie que si la requete figure **telle quelle**
     * dans le titre qu'il lui donne : « Sousou no Frieren » ne rend rien, « Frieren »
     * rend la fiche. Chaque titre est donc decline en plusieurs requetes, et un
     * resultat n'est retenu que s'il porte bien le titre cherche — jamais le premier
     * venu ([RechercheLis.trouver]).
     */
    private suspend fun ficheLis(titres: List<String>): String? {
        if (titres.isEmpty()) return null

        val retenu = RechercheLis.trouver(titres, MAX_TITRES_LIS) { requete ->
            runCatchingCancellable { lisScraper.rechercher(requete) }
                .onFailure { Log.w(TAG, "Recherche indisponible : $requete", it) }
                .getOrNull()
                .orEmpty()
        }

        if (retenu == null) {
            Log.w(TAG, "Site de lecture : aucune fiche pour ${titres.take(MAX_TITRES_LIS)}")
            return null
        }

        Log.i(TAG, "Site de lecture : « ${retenu.titre} » (${retenu.slug})")
        return lisScraper.urlFiche(retenu.slug)
    }

    /**
     * Un groupe du site devenu saison.
     *
     * La carte de la fiche suffit presque toujours. Si elle ne dit rien des langues,
     * la page du groupe est lue tout de suite plutot que de rendre une saison muette.
     */
    private suspend fun saisonLis(groupe: LisScraper.Groupe): Saison {
        val episodes = if (groupe.langues.isEmpty()) {
            runCatchingCancellable { episodesLis(groupe.url) }.getOrNull()
        } else {
            null
        }
        val chemin = PREFIXE_LIS + groupe.slug

        return Saison(
            nom = groupe.titre,
            path = chemin,
            baseUrl = groupe.url,
            nombreEpisodes = episodes?.size ?: groupe.nombreEpisodes,
            langues = languesLis(chemin, episodes?.flatMap { it.langues } ?: groupe.langues)
        )
    }

    /**
     * Un film publie sans groupe : la fiche porte elle-meme ses lecteurs.
     *
     * Il devient une saison d'un seul episode, dont les lecteurs sont deja connus.
     */
    private suspend fun filmLis(fiche: String): Saison? {
        val parLangue = lisScraper.film(fiche)
        val chemin = PREFIXE_LIS + CHEMIN_FILM
        val langues = languesLis(chemin, parLangue.keys)
        if (langues.isEmpty()) return null

        episodesLisParGroupe[fiche] = listOf(
            LisScraper.EpisodeListe(numero = 1, url = fiche, langues = parLangue.keys.toList())
        )
        lecteursLisParEpisode[fiche] = parLangue

        return Saison(
            nom = NOM_FILM,
            path = chemin,
            baseUrl = fiche,
            nombreEpisodes = 1,
            langues = langues
        )
    }

    private suspend fun episodesLis(anime: Anime, langue: Langue): List<Episode> {
        val chemin = langue.path.substringBeforeLast('/')
        val saison = saisons(anime).firstOrNull { it.path == chemin }
            ?: throw DataException.EpisodesUnavailable("Saison introuvable pour ${langue.path}")

        return episodesLis(saison.baseUrl)
            // Un episode qui ne dit rien de ses langues est garde : on ne retire que
            // ce dont on sait qu'il n'existe pas dans cette version.
            .filter { episode ->
                episode.langues.isEmpty() || episode.langues.any { code(it) == langue.code }
            }
            .map { episode ->
                Episode(
                    numero = episode.numero,
                    lecteurs = lecteursLisParEpisode[episode.url]
                        ?.let { versLecteurs(it, langue) }
                        .orEmpty(),
                    url = episode.url
                )
            }
    }

    private suspend fun episodesLis(urlGroupe: String): List<LisScraper.EpisodeListe> =
        episodesLisParGroupe[urlGroupe]
            ?: lisScraper.episodes(urlGroupe).also { episodesLisParGroupe[urlGroupe] = it }

    /** Langues connues de l'application parmi [segments], version originale en tete. */
    private fun languesLis(chemin: String, segments: Collection<String>): List<Langue> {
        val codes = segments.mapNotNull(::code).toSet()
        return LangueCode.entries
            .filter { it in codes }
            .map { Langue(code = it, path = "$chemin/${it.pathSegment}") }
    }

    private fun versLecteurs(parLangue: Map<String, List<String>>, langue: Langue): List<Lecteur> =
        parLangue
            .filterKeys { segment -> code(segment) == langue.code }
            .values
            .flatten()
            .distinct()
            .map { url -> Lecteur(index = 0, url = url) }
            .filter { it.estExploitable }
            .mapIndexed { ordre, lecteur -> lecteur.copy(index = ordre + 1) }

    /**
     * Langue designee par un segment du site de lecture.
     *
     * Les lecteurs trouves hors de toute declaration de langue sont ranges sous la
     * version originale : c'est ce que le site sert quand il ne precise rien.
     */
    private fun code(segment: String): LangueCode? =
        if (segment == LisScraper.LANGUE_PAR_DEFAUT) {
            LangueCode.VOSTFR
        } else {
            LangueCode.entries.firstOrNull { it.pathSegment.equals(segment, ignoreCase = true) }
        }

    companion object {
        private const val TAG = "CatalogueRepository"

        /**
         * Marque le chemin des saisons venues du site de lecture.
         *
         * Les deux sites nomment parfois un groupe pareil — `film` — et sans cette
         * marque deux saisons porteraient le meme chemin, donc la meme clef a l'ecran.
         */
        private const val PREFIXE_LIS = "lis:"

        /** Titres de la fiche soumis au site de lecture, chacun en plusieurs requetes. */
        private const val MAX_TITRES_LIS = 8

        /**
         * Temps laisse au site de lecture. Au-dela, la fiche s'affiche avec les
         * seules saisons du catalogue plutot que de rester en chargement.
         */
        private const val DELAI_LIS = 20_000L

        private const val NOM_FILM = "Film"
        private const val CHEMIN_FILM = "film"

        private val SAISON_NUMEROTEE =
            Regex("""(saison|saga)\D{0,3}(\d+)""", RegexOption.IGNORE_CASE)

        /**
         * Ce qui identifie une saison d'un site a l'autre.
         *
         * Les noms different dans le detail — « Saga 1 (East Blue) » ici,
         * « Saga 1 East Blue [Episode 1 à 61] » la — mais pas le type ni le numero.
         * Visible pour les tests.
         */
        internal fun cle(nom: String): String {
            val numerote = SAISON_NUMEROTEE.find(nom)
                // « Film » et « Films » designent le meme groupe.
                ?: return Slugifier.slugify(nom).removeSuffix("s")

            val kai = if (nom.contains("kai", ignoreCase = true)) "kai-" else ""
            return kai + numerote.groupValues[1].lowercase() + "-" +
                numerote.groupValues[2].toInt()
        }
    }
}
