package com.allanime.tv.domain.model

/**
 * Modeles de domaine issus d'AniList (sections 5 et 30 du cahier des charges).
 *
 * Tous les champs qu'AniList peut renvoyer a `null` restent nullables : l'interface
 * doit savoir afficher une fiche incomplete sans jamais planter. Les collections,
 * elles, sont toujours non nulles (vides au pire) pour simplifier l'affichage.
 */
data class Anime(
    val id: Int,
    val idMal: Int?,
    val title: AnimeTitle,
    val synonyms: List<String>,
    val description: String?,
    val format: String?,
    val status: String?,
    val episodes: Int?,
    val duration: Int?,
    val season: String?,
    val seasonYear: Int?,
    val source: String?,
    val hashtag: String?,
    val countryOfOrigin: String?,
    val isAdult: Boolean,
    val averageScore: Int?,
    val meanScore: Int?,
    val popularity: Int?,
    val favourites: Int?,
    val trending: Int?,
    val genres: List<String>,
    val tags: List<AnimeTag>,
    val bannerImage: String?,
    val coverImage: CoverImage,
    val startDate: FuzzyDate?,
    val endDate: FuzzyDate?,
    val studios: List<Studio>,
    val characters: List<CharacterRole>,
    val staff: List<StaffRole>,
    val rankings: List<Ranking>,
    val recommendations: List<Recommendation>,
    val externalLinks: List<ExternalLink>,
    val streamingEpisodes: List<StreamingEpisode>,
    val trailer: Trailer?,
    val siteUrl: String?,
    val updatedAt: Long?
) {
    /** Titre affiche : anglais, sinon romaji, sinon natif. */
    val displayTitle: String
        get() = title.english ?: title.romaji ?: title.nativeTitle ?: "#$id"

    /**
     * Titre principal servant a construire le slug du catalogue : le **romaji** en
     * priorite, le titre anglais en repli.
     */
    val catalogueTitle: String?
        get() = listOfNotNull(title.romaji, title.english, title.nativeTitle)
            .firstOrNull { it.isNotBlank() }

    /**
     * Titres candidats pour retrouver l'anime dans le catalogue, du plus probable au
     * moins probable.
     *
     * Le catalogue est heterogene : certaines fiches utilisent le romaji
     * (`shingeki-no-kyojin`), d'autres l'anglais (`classroom-of-the-elite`), d'autres
     * encore un nom court (`demon-slayer`, `frieren`, `mushoku-tensei`). Aucune regle
     * fixe ne couvre les trois cas, d'ou cette liste ordonnee : le resolveur essaie
     * les candidats jusqu'a en trouver un qui existe reellement.
     *
     * La forme raccourcie correspond a la partie situee avant un `:` ou un tiret
     * cadratin, qui donne precisement `mushoku-tensei` ou `demon-slayer`.
     */
    val candidatsCatalogue: List<String>
        get() = buildList {
            ajouterCandidat(title.romaji)
            ajouterCandidat(title.english)
            synonyms.forEach { synonyme -> ajouterCandidat(synonyme) }
            ajouterCandidat(title.nativeTitle)
        }.distinct()

    /**
     * Titres de l'oeuvre tels qu'AniList les donne, **sans leurs formes raccourcies**.
     *
     * Une forme raccourcie n'identifie pas une oeuvre a elle seule : « THE LAST »,
     * debut de « THE LAST: NARUTO THE MOVIE », est aussi le debut de « The Last
     * Summoner ». Ces titres-ci peuvent etre cherches dans un titre plus long ; les
     * formes courtes de [candidatsCatalogue], elles, ne valent que par egalite.
     */
    val titresComplets: List<String>
        get() = (listOfNotNull(title.romaji, title.english) + synonyms + listOfNotNull(title.nativeTitle))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /** `true` pour un anime de genre Hentai, que l'application ne propose pas. */
    val estHentai: Boolean
        get() = genres.any { it.equals("Hentai", ignoreCase = true) }

    /** Meilleure image de jaquette disponible. */
    val bestCover: String?
        get() = coverImage.extraLarge ?: coverImage.large ?: coverImage.medium

    /** Image large pour l'accueil : banniere si presente, sinon jaquette. */
    val heroImage: String?
        get() = bannerImage ?: bestCover

    val mainStudio: Studio?
        get() = studios.firstOrNull { it.isMain } ?: studios.firstOrNull()

    /**
     * Vignettes d'episodes fournies par AniList, indexees par numero.
     *
     * AniList les livre sous forme de titres libres (« Episode 3 - ... ») : seules
     * celles dont le numero est reellement lisible sont retenues, les autres sont
     * ignorees plutot que numerotees au hasard.
     *
     * Attention : une fiche AniList couvre une seule saison, alors que le catalogue
     * en regroupe plusieurs sous le meme identifiant. Ces vignettes ne sont donc
     * fiables que lorsque le catalogue ne propose qu'une saison — c'est a l'appelant
     * de le verifier.
     */
    val episodesDiffuses: Map<Int, StreamingEpisode>
        get() = streamingEpisodes
            .mapNotNull { episode ->
                val numero = NUMERO_EPISODE.find(episode.title.orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                numero?.let { it to episode }
            }
            .toMap()
}

/** « Episode 3 », « Ep. 3 », « ep3 » : les trois formes rencontrees chez AniList. */
private val NUMERO_EPISODE = Regex("""(?:episode|ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE)

/**
 * Ajoute un titre candidat, ainsi que sa forme raccourcie lorsqu'elle differe :
 * « Mushoku Tensei: Isekai Ittara... » donne aussi « Mushoku Tensei ».
 */
private fun MutableList<String>.ajouterCandidat(titre: String?) {
    val valeur = titre?.trim()?.takeIf { it.isNotBlank() } ?: return
    add(valeur)

    val court = valeur.split(':', '—', '–').first().trim()
    if (court.isNotBlank() && court != valeur) add(court)
}

data class AnimeTitle(
    val romaji: String?,
    val english: String?,
    val nativeTitle: String?
)

data class CoverImage(
    val extraLarge: String?,
    val large: String?,
    val medium: String?,
    /** Couleur dominante fournie par AniList, au format `#rrggbb`. */
    val color: String?
)

data class FuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?
) {
    val isEmpty: Boolean get() = year == null && month == null && day == null

    /** Rendu `jj/mm/aaaa`, tolerant aux composantes absentes. */
    fun formatted(): String? {
        if (isEmpty) return null
        val parts = listOfNotNull(
            day?.let { "%02d".format(it) },
            month?.let { "%02d".format(it) },
            year?.toString()
        )
        return parts.joinToString("/")
    }
}

data class AnimeTag(
    val id: Int?,
    val name: String,
    val description: String?,
    val rank: Int?,
    val isMediaSpoiler: Boolean
)

data class Studio(
    val id: Int?,
    val name: String,
    val isAnimationStudio: Boolean,
    val siteUrl: String?,
    val isMain: Boolean
)

data class CharacterRole(
    val role: String?,
    val character: Character,
    val voiceActors: List<VoiceActor>
)

data class Character(
    val id: Int?,
    val fullName: String,
    val nativeName: String?,
    val image: String?,
    val gender: String?,
    val age: String?,
    val description: String?
)

data class VoiceActor(
    val id: Int?,
    val fullName: String,
    val nativeName: String?,
    val image: String?
)

data class StaffRole(
    val role: String?,
    val member: StaffMember
)

data class StaffMember(
    val id: Int?,
    val fullName: String,
    val nativeName: String?,
    val image: String?,
    val description: String?
)

data class Ranking(
    val rank: Int?,
    val type: String?,
    val format: String?,
    val year: Int?,
    val season: String?,
    val allTime: Boolean,
    val context: String?
)

data class Recommendation(
    val rating: Int?,
    val mediaId: Int?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val coverImage: String?
) {
    val displayTitle: String
        get() = titleEnglish ?: titleRomaji ?: "?"
}

data class ExternalLink(
    val id: Int?,
    val url: String?,
    val site: String?,
    val type: String?,
    val language: String?,
    val color: String?,
    val icon: String?
)

data class StreamingEpisode(
    val title: String?,
    val thumbnail: String?,
    val url: String?,
    val site: String?
) {
    /**
     * Titre debarrasse de son prefixe de numerotation : « Episode 3 - To You » donne
     * « To You ». Vaut `null` quand il ne reste rien d'informatif.
     */
    val titreCourt: String?
        get() = title
            ?.replace(PREFIXE_NUMERO, "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}

/** Le prefixe « Episode 3 - » et ses variantes, separateur compris. */
private val PREFIXE_NUMERO =
    Regex("""^\s*(?:episode|ep\.?)\s*\d+\s*[-–—:.]?\s*""", RegexOption.IGNORE_CASE)

data class Trailer(
    val id: String?,
    val site: String?,
    val thumbnail: String?
) {
    /**
     * URL embarquable de la bande-annonce, lisible dans le WebView du lecteur.
     * Renvoie `null` si la plateforme n'est pas reconnue : on n'invente pas d'URL.
     */
    val embedUrl: String?
        get() {
            val videoId = id?.takeIf { it.isNotBlank() } ?: return null
            return when (site?.lowercase()) {
                "youtube" -> "https://www.youtube.com/embed/$videoId?autoplay=1&fs=1&rel=0"
                "dailymotion" -> "https://www.dailymotion.com/embed/video/$videoId?autoplay=1"
                else -> null
            }
        }
}
