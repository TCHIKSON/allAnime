package com.allanime.tv.ui.accueil

import com.allanime.tv.domain.model.AnimeCatalogue

/**
 * Une rangee thematique de l'accueil, deduite du catalogue deja telecharge.
 *
 * Le libelle n'est pas porte par le modele : il depend des ressources de langue, et
 * c'est l'ecran qui le compose.
 */
sealed interface RangeeAccueil {

    /** Clef stable, pour que la liste paresseuse ne recycle pas les mauvaises cartes. */
    val cle: String

    val animes: List<AnimeCatalogue>

    /** « Parce que vous avez regardé … » : voisinage du dernier anime ouvert. */
    data class Similaires(
        val titreSource: String,
        override val animes: List<AnimeCatalogue>
    ) : RangeeAccueil {
        override val cle: String get() = "similaires"
    }

    /** Une rangee par genre, le genre etant celui du catalogue, deja en francais. */
    data class Genre(
        val genre: String,
        override val animes: List<AnimeCatalogue>
    ) : RangeeAccueil {
        override val cle: String get() = "genre-$genre"
    }

    /** Ce que les rangees precedentes n'ont pas classe : le catalogue reste parcourable. */
    data class Autres(
        override val animes: List<AnimeCatalogue>
    ) : RangeeAccueil {
        override val cle: String get() = "autres"
    }
}

/**
 * Construction des rangees thematiques de l'accueil.
 *
 * Tout est calcule a partir de la liste du catalogue deja telechargee et de
 * l'historique local, sans un seul appel reseau supplementaire : les cartes du
 * catalogue portent deja leurs genres, leurs types et leur synopsis.
 *
 * Les longs metrages n'ont plus de rangee propre : la barre laterale y mene
 * directement, et les repeter ici prenait la place d'un genre sans rien apporter.
 * Ils sont donc classes par genre, comme le reste.
 *
 * Regle centrale : **un anime n'apparait que dans une seule rangee de
 * classement**. Les genres sont parcourus du plus represente au moins represente et
 * chacun ne recoit que ce que les precedents n'ont pas pris. Un anime etiquete
 * « Action, Comédie, Drame » se retrouve donc une seule fois, dans la rangee la plus
 * fournie des trois, et sa fiche continue d'afficher ses trois genres.
 *
 * Deux rangees echappent a ce classement parce qu'elles ne le sont pas : « Reprendre
 * votre visionnage », composee par l'historique, et [RangeeAccueil.Similaires], qui
 * repond a une question differente — quoi regarder apres celui-la.
 *
 * Fonction pure, donc verifiable hors appareil (`RangeesAccueilTest`).
 */
object RangeesAccueil {

    /** En dessous de ce nombre de titres, une rangee ne vaut pas la place qu'elle prend. */
    const val MIN_PAR_RANGEE = 4

    /**
     * Au-dela, l'accueil devient un annuaire. Le plafond est plus haut qu'a l'epoque
     * des tendances AniList : la source n'est plus une selection de dix titres mais
     * le catalogue entier, et le classement sans doublon repartit les animes au lieu
     * de les repeter.
     */
    const val MAX_RANGEES_GENRE = 15

    /** Une rangee de television se parcourt a la fleche : inutile d'aller plus loin. */
    const val MAX_PAR_RANGEE = 30

    /**
     * @param catalogue la liste du catalogue, seule source de l'accueil.
     * @param reference entree du dernier anime ouvert, ou `null` s'il n'y en a pas.
     * @param dejaVus slugs deja presents dans « Reprendre votre visionnage ».
     */
    fun construire(
        catalogue: List<AnimeCatalogue>,
        reference: AnimeCatalogue? = null,
        dejaVus: Set<String> = emptySet()
    ): List<RangeeAccueil> = buildList {
        similaires(catalogue, reference, dejaVus)?.let(::add)
        addAll(classement(catalogue))
    }

    /**
     * Voisinage du dernier anime ouvert, classe par nombre de genres communs.
     *
     * Renvoie `null` des qu'un element manque : pas de reference, aucun genre
     * renseigne, ou trop peu de voisins. Mieux vaut pas de rangee qu'une rangee qui
     * ne tient pas sa promesse.
     */
    private fun similaires(
        catalogue: List<AnimeCatalogue>,
        reference: AnimeCatalogue?,
        dejaVus: Set<String>
    ): RangeeAccueil.Similaires? {
        if (reference == null) return null

        val genres = reference.genres.toSet()
        if (genres.isEmpty()) return null

        val voisins = catalogue
            .filterNot { it.slug == reference.slug }
            .map { anime -> anime to anime.genres.count { it in genres } }
            .filter { (_, communs) -> communs > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        // Ce qui n'a pas encore ete commence est plus interessant a proposer, la
        // rangee « Reprendre » couvrant deja le reste. Mais un spectateur assidu a
        // souvent commence la moitie de ce voisinage : plutot que de faire
        // disparaitre la rangee, on retombe alors sur le voisinage complet.
        val inedits = voisins.filterNot { it.slug in dejaVus }
        val retenus = (if (inedits.size >= MIN_PAR_RANGEE) inedits else voisins)
            .take(MAX_PAR_RANGEE)

        if (retenus.size < MIN_PAR_RANGEE) return null

        return RangeeAccueil.Similaires(titreSource = reference.titre, animes = retenus)
    }

    /**
     * Classement du catalogue : les genres dominants, puis le reste.
     *
     * Chaque anime retire est retire pour de bon : c'est ce qui garantit qu'il ne
     * figure que dans une rangee.
     */
    private fun classement(catalogue: List<AnimeCatalogue>): List<RangeeAccueil> {
        val restants = catalogue.toMutableList()
        val rangees = mutableListOf<RangeeAccueil>()

        genresParEffectif(restants).forEach { genre ->
            if (rangees.count { it is RangeeAccueil.Genre } >= MAX_RANGEES_GENRE) return@forEach

            // L'effectif est recalcule implicitement : ce qu'un genre precedent a
            // pris ne compte plus, une rangee peut donc passer sous le seuil et etre
            // abandonnee sans bloquer les suivantes.
            prelever(restants) { genre in it.genres }
                ?.let { animes -> rangees += RangeeAccueil.Genre(genre, animes) }
        }

        if (restants.size >= MIN_PAR_RANGEE) {
            rangees += RangeeAccueil.Autres(restants.take(MAX_PAR_RANGEE))
        }

        return rangees
    }

    /**
     * Retire de [restants] les animes retenus par [critere] et les renvoie, ou `null`
     * s'ils sont trop peu nombreux — auquel cas rien n'est retire.
     */
    private fun prelever(
        restants: MutableList<AnimeCatalogue>,
        critere: (AnimeCatalogue) -> Boolean
    ): List<AnimeCatalogue>? {
        val retenus = restants.filter(critere).take(MAX_PAR_RANGEE)
        if (retenus.size < MIN_PAR_RANGEE) return null

        restants.removeAll(retenus.toSet())
        return retenus
    }

    /** Genres du catalogue, du plus represente au moins represente puis par ordre alphabetique. */
    private fun genresParEffectif(catalogue: List<AnimeCatalogue>): List<String> =
        catalogue
            .flatMap { anime -> anime.genres.distinct() }
            .groupingBy { genre -> genre }
            .eachCount()
            .filterValues { effectif -> effectif >= MIN_PAR_RANGEE }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
            )
            .map { it.key }
}
