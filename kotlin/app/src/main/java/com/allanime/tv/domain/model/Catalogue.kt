package com.allanime.tv.domain.model

/**
 * Modeles du catalogue : saisons, langues, episodes et lecteurs
 * (sections 10 a 12 du cahier des charges).
 */

/**
 * Langues possibles d'une saison.
 *
 * Les valeurs reprennent exactement celles testees par le script de reference
 * `scripts/saisson.js` : [pathSegment] est le suffixe d'URL sonde, [jsonKey] la clef
 * correspondante dans le JSON produit par ce script (`pathVO`, `pathVF`, ...).
 */
enum class LangueCode(
    val code: String,
    val pathSegment: String,
    val jsonKey: String,
    val label: String
) {
    VOSTFR("VOSTFR", "vostfr", "pathVO", "VOSTFR"),
    VF("VF", "vf", "pathVF", "VF"),
    VA("VA", "va", "pathVA", "VA (anglais)"),
    VAR("VAR", "var", "pathVAR", "VAR (arabe)"),
    VKR("VKR", "vkr", "pathVKR", "VKR (coréen)"),
    VCN("VCN", "vcn", "pathVCN", "VCN (chinois)"),
    VQC("VQC", "vqc", "pathVQC", "VQC (québécois)"),
    VF1("VF1", "vf1", "pathVF1", "VF 1"),
    VF2("VF2", "vf2", "pathVF2", "VF 2");

    companion object {
        fun fromCode(code: String): LangueCode? =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}

/**
 * Une langue reellement disponible pour une saison donnee.
 *
 * @param path chemin relatif au catalogue de l'anime, par exemple `saison1/vf`.
 */
data class Langue(
    val code: LangueCode,
    val path: String
)

/**
 * Une saison telle que decrite par le catalogue.
 *
 * Correspondance avec le JSON du script de reference (section 10) :
 * `Saison` -> [nom], `nombreEpisodes` -> [nombreEpisodes], `path` -> [path],
 * `Langues` -> les codes de [langues], `BaseUrl` -> [baseUrl] et les clefs
 * optionnelles `pathVO` / `pathVF` / `pathVA`... -> le [Langue.path] de chaque entree.
 *
 * Aucune langue n'est supposee presente : [langues] peut etre vide.
 */
data class Saison(
    val nom: String,
    val path: String,
    val baseUrl: String,
    val nombreEpisodes: Int?,
    val langues: List<Langue>
) {
    /** Resume des langues, tel qu'affiche sur la fiche : `VOSTFR, VF`. */
    val languesResume: String
        get() = langues.joinToString(", ") { it.code.code }

    /** Langue proposee par defaut : VOSTFR si disponible, sinon la premiere trouvee. */
    val langueParDefaut: Langue?
        get() = langues.firstOrNull { it.code == LangueCode.VOSTFR } ?: langues.firstOrNull()
}

/**
 * Un episode et la liste ordonnee de ses lecteurs.
 *
 * C'est la transposition en memoire du `episode.json` du script de reference :
 * la clef `Episode 3` devient [numero] = 3 et le tableau d'URL devient [lecteurs].
 */
data class Episode(
    val numero: Int,
    val lecteurs: List<Lecteur>,
    /**
     * Page de l'episode sur le site, quand ses lecteurs ne sont pas encore lus.
     *
     * Le site declare les lecteurs episode par episode : les lire tous pour afficher
     * une saison couterait une requete par episode. La liste est donc rendue sans
     * eux, et `CatalogueRepository.lecteurs` les complete au moment de lancer la
     * lecture.
     */
    val url: String? = null
) {
    /** Lisible : ses lecteurs sont connus, ou sa page permet de les obtenir. */
    val estDisponible: Boolean get() = lecteurs.isNotEmpty() || url != null
}

/**
 * Un lecteur video embarquable.
 *
 * [url] n'est jamais affichee telle quelle a l'utilisateur (section 13) : l'interface
 * se sert de [index] et de [hostLabel].
 */
data class Lecteur(
    val index: Int,
    val url: String
) {
    /**
     * `true` si l'adresse designe reellement une video.
     *
     * Le catalogue laisse en place des pages de saison qui ne sont que des gabarits,
     * avec des adresses **videes de leur identifiant** :
     *
     * ```
     * https://ansembed.net/embed-.html          au lieu de  .../embed-myd4qa40u7h4.html
     * https://sendvid.com/embed/                au lieu de  .../embed/kvwii1ne
     * https://vk.com/video_ext.php?oid=&hd=3    au lieu de  ...?oid=123456&hd=3
     * ```
     *
     * Elles repondent, mais ne menent nulle part. Trois marques les trahissent, et
     * ce sont les seules sur lesquelles on peut se fier sans connaitre chaque
     * hebergeur : un parametre sans valeur, un chemin qui s'arrete sur un separateur,
     * et l'absence pure et simple d'identifiant.
     */
    val estExploitable: Boolean
        get() = runCatching {
            val adresse = java.net.URI(url)
            val chemin = adresse.path.orEmpty()
            val parametres = adresse.rawQuery
            val ancre = adresse.rawFragment

            // `?oid=` : la place de l'identifiant est la, mais elle est vide.
            val parametreVide = parametres
                ?.split('&')
                ?.any { it.contains('=') && it.substringAfter('=').isEmpty() } == true
            if (parametreVide) return@runCatching false

            val dernierSegment = chemin.trimEnd('/').substringAfterLast('/')
            val sansExtension = dernierSegment.substringBeforeLast('.', dernierSegment)

            // `/embed/` : le chemin attend un dernier segment qui n'est pas venu.
            if (chemin.endsWith('/') && ancre.isNullOrEmpty() && parametres.isNullOrEmpty()) {
                return@runCatching false
            }

            // `embed-.html` : le separateur est la, l'identifiant derriere manque.
            if (sansExtension.isNotEmpty() && sansExtension.last() in SEPARATEURS_IDENTIFIANT) {
                return@runCatching false
            }

            // Il faut un identifiant quelque part : ancre, parametre ou chemin.
            !ancre.isNullOrEmpty() || !parametres.isNullOrEmpty() || sansExtension.isNotEmpty()
        }.getOrDefault(false)

    /** Nom court de la plateforme, deduit de l'hote : `video.sibnet.ru` donne `sibnet`. */
    val hostLabel: String
        get() = runCatching {
            val host = java.net.URI(url).host?.removePrefix("www.") ?: return@runCatching ""
            val labels = host.split('.')
            if (labels.size >= 2) labels[labels.size - 2] else host
        }.getOrDefault("")
}

/**
 * Une selection complete : anime + saison + langue + episode.
 * Sert de contexte au lecteur video et a l'enchainement automatique des episodes.
 */
data class SelectionLecture(
    val animeId: Int,
    val animeTitre: String,
    val saison: Saison,
    val langue: Langue,
    val episode: Episode,
    val totalEpisodes: Int
)

/** Caracteres qui, en fin de segment, annoncent un identifiant absent. */
private val SEPARATEURS_IDENTIFIANT = setOf('-', '_', '=', '/')
