package com.allanime.tv.domain.model

import com.allanime.tv.core.Slugifier

/**
 * Une entree du moteur de recherche du site.
 *
 * Correspondance avec le JSON du script de reference `parserSearch.js` :
 * `slug` -> [slug], `title` -> [titre], `subtitle` -> [sousTitres] et
 * `image` -> [image]. Le script rend `subtitle` sous forme d'une seule chaine, les
 * titres alternatifs y etant separes par des virgules ; ils sont decoupes ici, car
 * c'est entree par entree qu'on les compare.
 */
data class ResultatRecherche(
    val slug: String,
    val titre: String,
    val sousTitres: List<String>,
    val image: String?
) {
    /**
     * Toutes les facons de nommer cette fiche, slugifiees : c'est sous cette forme
     * que la comparaison se fait, les accents, la casse et la ponctuation ne devant
     * pas la faire echouer.
     *
     * Le sous-titre recompose figure a cote de ses morceaux, car **son decoupage est
     * ambigu** : le site separe les titres alternatifs par des virgules, mais
     * certains en contiennent — « Clevatess: The King of Devil Beasts, The Baby and
     * the Brave of Undead » en est un seul. Garder les deux permet de reconnaitre
     * aussi bien un titre correctement isole qu'un titre coupe en deux.
     */
    val formes: List<String>
        get() = (listOf(titre) + sousTitres + sousTitres.joinToString(", "))
            .flatMap { Slugifier.variantes(it) }
            .distinct()
}

/**
 * Choix de la fiche du catalogue correspondant a un titre.
 *
 * Le moteur de recherche du site est tolerant : il repond a peu pres a tout, et son
 * premier resultat n'est pas forcement le bon. Cet objet decide lequel retenir, ou
 * qu'aucun ne convient — auquel cas l'appelant reprend le sondage de slugs.
 *
 * **Le catalogue regroupe toutes les saisons d'une serie sous une seule fiche.** Une
 * recherche sur « Shingeki no Kyojin Season 3 Part 2 » ne trouvera donc jamais ce
 * titre tel quel : c'est sous « Shingeki no Kyojin » que la fiche existe. D'ou le
 * second temps, qui retire les marqueurs de suite et recommence — mais seulement
 * quand le titre en portait, pour ne pas elargir la recherche sans raison.
 */
object CorrespondanceCatalogue {

    /**
     * @param titre titre envoye au moteur de recherche.
     * @param resultats reponse du moteur, dans son ordre.
     * @return l'entree retenue, ou `null` si aucune ne correspond.
     */
    fun choisir(titre: String, resultats: List<ResultatRecherche>): ResultatRecherche? {
        if (resultats.isEmpty()) return null

        val cherche = Slugifier.slugify(titre)
        if (cherche.isEmpty()) return null

        // Premier temps : le titre demande, tel quel.
        trouver(resultats, cherche)?.let { return it }

        // Second temps : le meme titre debarrasse de ses marqueurs de suite. Il n'a
        // lieu que si le titre en portait ; sinon les deux formes sont identiques et
        // l'on conclurait deux fois la meme chose.
        val base = Slugifier.slugify(sansMarqueurDeSuite(titre))
        if (base.isEmpty() || base == cherche) return null

        return trouver(resultats, base)
    }

    /**
     * Retire les marqueurs de suite en fin de titre, un par un : « Shingeki no Kyojin
     * Season 3 Part 2 » perd d'abord « Part 2 », puis « Season 3 ».
     *
     * Visible pour les tests : c'est la regle la plus delicate de ce fichier.
     */
    fun sansMarqueurDeSuite(titre: String): String {
        var reste = titre.trim()

        while (true) {
            val coupe = MARQUEUR_DE_SUITE.replace(reste, "").trim()
            // Un titre entierement fait de marqueurs — « Saison 2 » seul — n'a pas de
            // base : on rend le titre inchange plutot qu'un reste qui correspondrait
            // a n'importe quelle fiche.
            if (coupe.isEmpty() || MOT_DE_MARQUEUR.matches(coupe)) return titre.trim()
            if (coupe == reste) return reste
            reste = coupe
        }
    }

    /**
     * Cherche [forme] parmi les entrees, du plus precis au plus large : egalite
     * d'abord, puis presence en tant que mot entier.
     */
    private fun trouver(resultats: List<ResultatRecherche>, forme: String): ResultatRecherche? =
        resultats.firstOrNull { resultat -> resultat.formes.any { it == forme } }
            ?: resultats.firstOrNull { resultat ->
                resultat.formes.any { contientLeTitre(it, forme) }
            }

    /**
     * `true` si [forme] apparait dans [candidat] **en entier**.
     *
     * La comparaison porte sur des slugs, dont les mots sont separes par des tirets :
     * exiger un tiret de part et d'autre evite que « no-6 » soit reconnu dans
     * « no-6ix » ou que « one » le soit dans « one-piece » sans etre un mot a part
     * entiere... et evite surtout qu'un titre court corresponde a n'importe quoi.
     */
    private fun contientLeTitre(candidat: String, forme: String): Boolean = when {
        candidat == forme -> true
        candidat.startsWith("$forme-") -> true
        candidat.endsWith("-$forme") -> true
        else -> candidat.contains("-$forme-")
    }

    /**
     * Un marqueur de suite en fin de titre, precede d'un separateur.
     *
     * Couvre les formes rencontrees : `Season 2`, `Saison 2`, `2nd Season`,
     * `Final Season`, `Part 2`, `Partie 2`, `Cour 2`, un chiffre romain (`II`, `IV`)
     * ou un simple nombre (`Psycho-Pass 2`).
     */
    /** Ce qui resterait d'un titre entierement fait de marqueurs. */
    private val MOT_DE_MARQUEUR =
        Regex("""(?:saison|season|partie|part|cour)""", RegexOption.IGNORE_CASE)

    private val MARQUEUR_DE_SUITE = Regex(
        """[\s:\-–—]+(?:the\s+)?(?:""" +
            """(?:saison|season)\s*\d+""" +
            """|\d+(?:st|nd|rd|th|e|er|eme)?\s*(?:saison|season)""" +
            """|(?:first|second|third|fourth|final|derniere|premiere)\s*(?:saison|season)""" +
            """|(?:partie|part|cour)\s*\d+""" +
            """|[ivx]{1,4}""" +
            """|\d{1,2}""" +
            """)\s*$""",
        RegexOption.IGNORE_CASE
    )
}
