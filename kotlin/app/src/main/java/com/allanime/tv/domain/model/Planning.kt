package com.allanime.tv.domain.model

import java.util.Calendar
import kotlinx.serialization.Serializable

/**
 * Planning hebdomadaire des sorties, lu sur la page `/planning` du site.
 *
 * Il alimente deux endroits : la rangee « Sorties du jour » de l'accueil et l'ecran
 * de planning complet. Le modele porte l'annotation de serialisation parce qu'il est
 * conserve sur le televiseur entre deux lancements (voir `PlanningRepository`) :
 * l'accueil affiche ainsi les sorties du jour immediatement, sans attendre le reseau.
 *
 * `java.time` n'est pas disponible a partir de l'API 23 sans desucrage : les calculs
 * de date passent donc par [Calendar], comme ailleurs dans l'application.
 */

/** Jour de la semaine, tel que le site l'intitule. */
@Serializable
enum class JourSemaine(val jourCalendar: Int, val libelle: String) {
    LUNDI(Calendar.MONDAY, "Lundi"),
    MARDI(Calendar.TUESDAY, "Mardi"),
    MERCREDI(Calendar.WEDNESDAY, "Mercredi"),
    JEUDI(Calendar.THURSDAY, "Jeudi"),
    VENDREDI(Calendar.FRIDAY, "Vendredi"),
    SAMEDI(Calendar.SATURDAY, "Samedi"),
    DIMANCHE(Calendar.SUNDAY, "Dimanche");

    companion object {

        /** Jour en cours sur le televiseur. */
        fun courant(calendrier: Calendar = Calendar.getInstance()): JourSemaine {
            val jour = calendrier.get(Calendar.DAY_OF_WEEK)
            return entries.firstOrNull { it.jourCalendar == jour } ?: LUNDI
        }

        /**
         * Reconnait l'intitule d'une colonne du planning.
         *
         * La comparaison ignore la casse et les accents : le site ecrit « Lundi »
         * aujourd'hui, mais rien ne garantit qu'il n'ecrira pas « LUNDI » demain.
         */
        fun depuisLibelle(brut: String?): JourSemaine? {
            val normalise = com.allanime.tv.core.Slugifier.slugify(brut)
            if (normalise.isEmpty()) return null
            return entries.firstOrNull {
                com.allanime.tv.core.Slugifier.slugify(it.libelle) == normalise
            }
        }
    }
}

/**
 * Une sortie annoncee par le planning.
 *
 * @param heure heure annoncee, telle qu'ecrite par le site (`18h30`). Vaut `null`
 *   quand le site affiche `?`, ce qui arrive systematiquement pour les scans et pour
 *   les versions francaises dont l'horaire n'est pas arrete.
 * @param reporte `true` quand le site signale « Reporté ». L'episode reste annonce ce
 *   jour-la, mais sans garantie.
 * @param precision complement affiche par la carte : « Saison 2 », « Saga Elbaf »...
 * @param horodatage date de sortie en secondes Unix, quand le site la fournit.
 */
@Serializable
data class SortiePlanning(
    val titre: String,
    val slug: String,
    val type: String,
    val langue: String? = null,
    val image: String? = null,
    val heure: String? = null,
    val precision: String? = null,
    val reporte: Boolean = false,
    val horodatage: Long? = null
) {

    /** Les scans partagent le planning avec les animes ; seuls les animes se lisent. */
    val estAnime: Boolean
        get() = type.trim().equals(TYPE_ANIME, ignoreCase = true)

    /**
     * Clef stable pour les listes paresseuses : un meme titre apparait plusieurs fois
     * dans la meme journee, une fois par langue.
     */
    val cle: String
        get() = "$slug-${langue.orEmpty()}-${precision.orEmpty()}"

    private companion object {
        const val TYPE_ANIME = "Anime"
    }
}

/** Une colonne du planning : un jour et ses sorties. */
@Serializable
data class JourPlanning(
    val jour: JourSemaine,
    /** Date affichee par le site, au format `jj/mm`. Purement indicative. */
    val date: String,
    val sorties: List<SortiePlanning>
)

/**
 * Le planning complet.
 *
 * @param sansJourFixe oeuvres en cours dont le site ne connait pas le jour de sortie ;
 *   il les liste sous le planning.
 * @param recupereLe date de telechargement, en millisecondes, qui sert a decider si
 *   les donnees conservees sur la TV sont encore valables.
 */
@Serializable
data class Planning(
    val jours: List<JourPlanning>,
    val sansJourFixe: List<SortiePlanning> = emptyList(),
    val recupereLe: Long = 0L
) {

    val estVide: Boolean
        get() = jours.all { it.sorties.isEmpty() } && sansJourFixe.isEmpty()

    /**
     * Le meme planning, reduit aux animes.
     *
     * Le site publie animes et scans dans les memes colonnes. L'application ne sait
     * lire que les premiers : proposer une carte de scan menerait a une page sans
     * episode, ce qui ressemblerait a une panne alors que rien n'est casse.
     */
    fun animesSeulement(): Planning = copy(
        jours = jours.map { jour -> jour.copy(sorties = jour.sorties.filter { it.estAnime }) },
        sansJourFixe = sansJourFixe.filter { it.estAnime }
    )

    /** Sorties annoncees pour un jour donne, vide si le site ne l'a pas publie. */
    fun sortiesDu(jour: JourSemaine): List<SortiePlanning> =
        jours.firstOrNull { it.jour == jour }?.sorties.orEmpty()

    /**
     * Sorties des jours qui **precedent** [jour], du plus recent au plus ancien.
     *
     * C'est l'equivalent le plus honnete d'une rubrique « derniers ajouts » : le
     * catalogue ne date pas ses entrees, le planning est la seule source qui dise
     * quand quelque chose parait. Remonter plus loin que quelques jours ferait
     * repasser la semaine entiere, ce qui n'aurait plus rien de recent.
     *
     * Une serie annoncee deux jours de suite n'apparait qu'une fois, et le jour
     * courant est volontairement exclu : il a sa propre rangee.
     *
     * @param profondeur nombre de jours remontes, au plus une semaine.
     */
    fun sortiesDesJoursPrecedents(
        jour: JourSemaine,
        profondeur: Int
    ): List<SortiePlanning> {
        val ordre = JourSemaine.entries
        val recul = profondeur.coerceIn(0, ordre.size - 1)

        return (1..recul)
            .map { ecart -> ordre[(jour.ordinal - ecart + ordre.size) % ordre.size] }
            .flatMap { sortiesDu(it) }
            .distinctBy { it.slug }
    }

    /**
     * Compare deux plannings sur leur contenu seul, sans tenir compte de la date de
     * telechargement : c'est ce qui permet de ne reecrire le stockage que lorsque le
     * site a reellement publie autre chose.
     */
    fun memeContenu(autre: Planning): Boolean =
        jours == autre.jours && sansJourFixe == autre.sansJourFixe

    /**
     * `true` si ces donnees datent d'avant la derniere actualisation prevue du site.
     *
     * Le site annonce republier son planning « tous les lundis soir » : tant qu'on
     * n'a pas franchi ce jalon, une copie locale reste fidele et il est inutile de
     * faire patienter l'utilisateur.
     */
    fun estPerime(calendrier: Calendar = Calendar.getInstance()): Boolean =
        recupereLe < dernierePublication(calendrier)

    companion object {

        /** Heure locale a partir de laquelle le site a publie la semaine suivante. */
        const val HEURE_PUBLICATION = 21

        /** Horodatage de la derniere publication hebdomadaire passee (lundi soir). */
        fun dernierePublication(calendrier: Calendar = Calendar.getInstance()): Long {
            val jalon = calendrier.clone() as Calendar
            jalon.set(Calendar.HOUR_OF_DAY, HEURE_PUBLICATION)
            jalon.set(Calendar.MINUTE, 0)
            jalon.set(Calendar.SECOND, 0)
            jalon.set(Calendar.MILLISECOND, 0)

            while (jalon.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                jalon.add(Calendar.DAY_OF_MONTH, -1)
            }
            // Le lundi avant 21 h, le jalon calcule est encore devant nous.
            if (jalon.timeInMillis > calendrier.timeInMillis) {
                jalon.add(Calendar.DAY_OF_MONTH, -7)
            }
            return jalon.timeInMillis
        }
    }
}
