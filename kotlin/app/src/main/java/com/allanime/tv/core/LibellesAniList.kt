package com.allanime.tv.core

import java.util.Locale

/**
 * Traduction des enumerations AniList affichees sur la fiche.
 *
 * AniList livre ces champs en anglais et en majuscules : `TV`, `NOT_YET_RELEASED`,
 * `LIGHT_NOVEL`. Tels quels, ils sont illisibles dans une interface francaise, et
 * une fiche en affiche cinq a la suite.
 *
 * Meme principe que [Genres] : une valeur inconnue — une enumeration qu'AniList
 * etendra — n'est pas remplacee par une approximation, seulement remise en forme
 * par [humaniser] (`SOME_NEW_VALUE` donne « Some new value »).
 */
object LibellesAniList {

    private val FORMATS = mapOf(
        "TV" to "Série TV",
        "TV_SHORT" to "Format court",
        "MOVIE" to "Film",
        "SPECIAL" to "Épisode spécial",
        "OVA" to "OAV",
        "ONA" to "ONA",
        "MUSIC" to "Clip musical",
        "MANGA" to "Manga",
        "NOVEL" to "Roman",
        "ONE_SHOT" to "One shot"
    )

    private val STATUTS = mapOf(
        "FINISHED" to "Terminé",
        "RELEASING" to "En cours de diffusion",
        "NOT_YET_RELEASED" to "Pas encore diffusé",
        "CANCELLED" to "Annulé",
        "HIATUS" to "En pause"
    )

    private val SAISONS = mapOf(
        "WINTER" to "Hiver",
        "SPRING" to "Printemps",
        "SUMMER" to "Été",
        "FALL" to "Automne"
    )

    private val SOURCES = mapOf(
        "ORIGINAL" to "Création originale",
        "MANGA" to "Manga",
        "LIGHT_NOVEL" to "Light novel",
        "VISUAL_NOVEL" to "Visual novel",
        "VIDEO_GAME" to "Jeu vidéo",
        "OTHER" to "Autre",
        "NOVEL" to "Roman",
        "DOUJINSHI" to "Doujinshi",
        "ANIME" to "Anime",
        "WEB_NOVEL" to "Web novel",
        "LIVE_ACTION" to "Prise de vues réelles",
        "GAME" to "Jeu",
        "COMIC" to "Comic",
        "MULTIMEDIA_PROJECT" to "Projet multimédia",
        "PICTURE_BOOK" to "Livre illustré"
    )

    /** Les pays d'origine reellement rencontres sur AniList, en ISO 3166-1 alpha-2. */
    private val PAYS = mapOf(
        "JP" to "Japon",
        "KR" to "Corée du Sud",
        "CN" to "Chine",
        "TW" to "Taïwan",
        "US" to "États-Unis",
        "FR" to "France"
    )

    fun format(valeur: String?): String? = traduire(valeur, FORMATS)

    fun statut(valeur: String?): String? = traduire(valeur, STATUTS)

    fun saison(valeur: String?): String? = traduire(valeur, SAISONS)

    fun source(valeur: String?): String? = traduire(valeur, SOURCES)

    fun pays(valeur: String?): String? = traduire(valeur, PAYS)

    /** `Printemps 2013`, ou l'un des deux seul, ou rien. */
    fun saisonEtAnnee(saison: String?, annee: Int?): String? {
        val nom = saison(saison)
        return when {
            nom != null && annee != null -> "$nom $annee"
            else -> nom ?: annee?.toString()
        }
    }

    private fun traduire(valeur: String?, table: Map<String, String>): String? {
        val brut = valeur?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return table[brut.uppercase(Locale.ROOT)] ?: humaniser(brut)
    }

    /** `NOT_YET_RELEASED` devient « Not yet released » : lisible, sans rien inventer. */
    private fun humaniser(brut: String): String {
        val mots = brut.replace('_', ' ').lowercase(Locale.ROOT).trim()
        return mots.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }
}
