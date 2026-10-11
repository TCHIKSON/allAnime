package com.allanime.tv.domain.model

import com.allanime.tv.core.Slugifier

/**
 * Retrouver une serie sur le site de lecture a partir d'un titre de sa fiche.
 *
 * Le moteur de ce site est **litteral** : il ne rend une fiche que si la requete
 * figure telle quelle dans le titre qu'il lui donne, ponctuation comprise. Releve sur
 * le site :
 *
 * ```
 * « Sousou no Frieren »                 -> rien        « Frieren »   -> la fiche
 * « Re:ZERO -Starting Life ... World- » -> rien        « Re:Zero »   -> la fiche
 * « L'Attaque des Titans »              -> un derive   « titan »     -> la fiche
 * « My Hero Academia Season 7 »         -> rien        « My Hero Academia » -> la fiche
 * ```
 *
 * D'ou deux temps : poser plusieurs requetes pour un meme titre, de la plus precise a
 * la plus large ([requetes]), puis n'accepter qu'un resultat qui porte reellement ce
 * titre ([exacte], a defaut [approchee] ou [memesMots]) ; [trouver] enchaine les deux. Elargir la
 * requete sans resserrer le choix ferait ouvrir la mauvaise serie ; resserrer le choix
 * sans elargir la requete n'en trouverait aucune.
 */
object RechercheLis {

    /**
     * Requetes a poser pour [titre], dans l'ordre :
     *
     *  1. le titre tel quel ;
     *  2. sans marqueur de suite (« Season 7 », « Part 2 ») ni ponctuation aux bords ;
     *  3. sa partie situee avant un `:` ou un tiret ;
     *  4. ses mots les plus longs, un par un — le dernier recours, qui retrouve une
     *     fiche dont le site ecrit le titre autrement (« LAttaque des Titans »).
     */
    fun requetes(titre: String): List<String> {
        val entier = titre.trim()
        val base = CorrespondanceCatalogue.sansMarqueurDeSuite(entier).trim { !it.isLetterOrDigit() }
        val court = court(base)

        val mots = MOT.findAll(base)
            .map { it.value }
            .filter { it.length >= LONGUEUR_MOT_CLE && it.lowercase() !in MOTS_CREUX }
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }
            .take(MAX_MOTS_CLES)
            .toList()

        return (listOf(entier, base, court) + mots)
            .filter { it.length >= LONGUEUR_REQUETE }
            .distinctBy { it.lowercase() }
    }

    /**
     * Cherche la fiche d'une serie connue sous [titres], du plus sur au moins sur.
     *
     * Pour chaque titre, toutes ses requetes sont posees avant de conclure : une
     * fiche au titre **exact** est retenue aussitot, une fiche seulement **approchee**
     * n'est gardee qu'en reserve. Sans cette attente, « L'Attaque des Titans »
     * s'arreterait sur « L'Attaque des Titans - Junior High School », seule reponse a
     * sa premiere requete, alors que la serie elle-meme sort a la requete « Titans ».
     *
     * @param rechercher le moteur du site ; une panne vaut une reponse vide.
     * @param maxTitres nombre de titres essayes avant d'abandonner.
     */
    suspend fun trouver(
        titres: List<String>,
        maxTitres: Int,
        rechercher: suspend (String) -> List<ResultatRecherche>
    ): ResultatRecherche? {
        val reponses = mutableMapOf<String, List<ResultatRecherche>>()

        for (titre in titres.take(maxTitres)) {
            var reserve: ResultatRecherche? = null

            for (requete in requetes(titre)) {
                // Une requete deja posee pour un autre titre n'est pas renvoyee au
                // site, mais sa reponse est relue : elle peut convenir a celui-ci.
                val cle = requete.lowercase()
                val resultats = reponses[cle] ?: rechercher(requete).also { reponses[cle] = it }

                exacte(titre, resultats)?.let { return it }
                if (reserve == null) {
                    reserve = approchee(titre, resultats) ?: memesMots(titre, resultats)
                }
            }

            reserve?.let { return it }
        }

        return null
    }

    /**
     * Fiche de [resultats] qui porte exactement [titre] : tel quel, sans son
     * marqueur de suite, ou reduit a sa partie courte — le site nommant souvent une
     * serie par son seul debut, « Frieren » pour « Frieren: Beyond Journey's End ».
     */
    fun exacte(titre: String, resultats: List<ResultatRecherche>): ResultatRecherche? {
        val base = CorrespondanceCatalogue.sansMarqueurDeSuite(titre)

        return listOf(titre, base, court(base))
            .flatMap { Slugifier.variantes(it) }
            .filter { it.length >= LONGUEUR_REQUETE }
            .distinct()
            .firstNotNullOfOrNull { forme ->
                resultats.firstOrNull { resultat -> forme in resultat.formes }
            }
    }

    /**
     * Fiche de [resultats] dont le titre **contient** [titre] en entier : « Demon
     * Slayer : Kimetsu no Yaiba » pour « Kimetsu no Yaiba ». Les regles sont celles
     * du catalogue ([CorrespondanceCatalogue.choisir]).
     */
    fun approchee(titre: String, resultats: List<ResultatRecherche>): ResultatRecherche? =
        CorrespondanceCatalogue.choisir(titre, resultats)

    /**
     * Fiche de [resultats] dont le titre est fait **des memes mots** que [titre],
     * dans un autre ordre : « Naruto the Last: Le film » pour « The Last: Naruto the
     * Movie ». Les articles et les mots « film » ou « movie » ne comptent pas.
     *
     * Il faut au moins deux mots porteurs : un seul ne distingue rien.
     */
    fun memesMots(titre: String, resultats: List<ResultatRecherche>): ResultatRecherche? {
        val cherches = motsPorteurs(titre)
        if (cherches.size < MOTS_PORTEURS_MIN) return null

        return resultats.firstOrNull { motsPorteurs(it.titre) == cherches }
    }

    private fun motsPorteurs(titre: String): Set<String> =
        Slugifier.slugify(titre).split('-').filter { it.isNotEmpty() && it !in MOTS_VIDES }.toSet()

    /** « Frieren: Beyond Journey's End » donne « Frieren ». */
    private fun court(titre: String): String =
        titre.split(SEPARATEUR_DE_SOUS_TITRE).first().trim()

    private val SEPARATEUR_DE_SOUS_TITRE = Regex("""\s*[:—–]\s*|\s+-\s*|\s*-\s+""")
    private val MOT = Regex("""[\p{L}\p{N}]+""")

    /** Mots trop communs pour designer une serie. */
    private val MOTS_CREUX = setOf(
        "saison", "season", "partie", "part", "film", "films", "movie", "the", "des",
        "les", "une", "and", "with", "from", "dans", "pour", "avec", "anime", "special"
    )

    /** Mots qui ne disent rien de l'oeuvre : articles, et « film » sous ses formes. */
    private val MOTS_VIDES = setOf("the", "a", "an", "le", "la", "les", "l", "un", "une", "film", "movie")

    private const val MOTS_PORTEURS_MIN = 2
    private const val LONGUEUR_REQUETE = 3
    private const val LONGUEUR_MOT_CLE = 4
    private const val MAX_MOTS_CLES = 2
}
