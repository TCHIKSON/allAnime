package com.allanime.tv.domain.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regles de fraicheur du planning.
 *
 * Le site republie le sien « tous les lundis soir » : c'est ce jalon qui decide si la
 * copie conservee sur le televiseur est encore fidele, et donc si l'accueil peut
 * l'afficher sans faire patienter.
 */
class PlanningTest {

    /** 2026-09-21 est un lundi, comme dans le planning publie par le site. */
    private fun instant(jour: Int, heure: Int, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, jour, heure, minute, 0)
        }

    private fun planning(recupereLe: Long) = Planning(
        jours = listOf(JourPlanning(JourSemaine.LUNDI, "21/09", emptyList())),
        recupereLe = recupereLe
    )

    @Test
    fun `la derniere publication est le lundi soir precedent`() {
        val mercredi = instant(jour = 23, heure = 10)

        assertEquals(
            instant(jour = 21, heure = Planning.HEURE_PUBLICATION).timeInMillis,
            Planning.dernierePublication(mercredi)
        )
    }

    @Test
    fun `le lundi avant l heure de publication, le jalon est celui de la semaine passee`() {
        val lundiMatin = instant(jour = 21, heure = 10)

        assertEquals(
            instant(jour = 14, heure = Planning.HEURE_PUBLICATION).timeInMillis,
            Planning.dernierePublication(lundiMatin)
        )
    }

    @Test
    fun `le lundi soir, le jalon est celui du jour meme`() {
        val lundiSoir = instant(jour = 21, heure = Planning.HEURE_PUBLICATION, minute = 30)

        assertEquals(
            instant(jour = 21, heure = Planning.HEURE_PUBLICATION).timeInMillis,
            Planning.dernierePublication(lundiSoir)
        )
    }

    @Test
    fun `une copie prise apres la publication reste valable`() {
        val mercredi = instant(jour = 23, heure = 10)
        val copie = planning(instant(jour = 22, heure = 8).timeInMillis)

        assertFalse(copie.estPerime(mercredi))
    }

    @Test
    fun `une copie de la semaine precedente est perimee`() {
        val mercredi = instant(jour = 23, heure = 10)
        val copie = planning(instant(jour = 18, heure = 8).timeInMillis)

        assertTrue(copie.estPerime(mercredi))
    }

    @Test
    fun `un planning jamais telecharge est perime`() {
        assertTrue(planning(0L).estPerime(instant(jour = 23, heure = 10)))
    }

    @Test
    fun `deux plannings identiques ne different que par leur date de telechargement`() {
        assertTrue(planning(1L).memeContenu(planning(999L)))
    }

    @Test
    fun `un contenu different est detecte`() {
        val autre = Planning(
            jours = listOf(JourPlanning(JourSemaine.LUNDI, "28/09", emptyList())),
            recupereLe = 1L
        )

        assertFalse(planning(1L).memeContenu(autre))
    }

    @Test
    fun `un planning sans aucune sortie est vide`() {
        assertTrue(planning(1L).estVide)
    }

    @Test
    fun `le planning reduit aux animes laisse les scans de cote`() {
        val anime = SortiePlanning(titre = "Liar Game", slug = "liar-game", type = "Anime")
        val scan = SortiePlanning(titre = "Ordeal", slug = "ordeal", type = "Scans")

        val complet = Planning(
            jours = listOf(JourPlanning(JourSemaine.LUNDI, "21/09", listOf(anime, scan))),
            sansJourFixe = listOf(scan)
        )

        val reduit = complet.animesSeulement()

        assertEquals(listOf(anime), reduit.sortiesDu(JourSemaine.LUNDI))
        assertTrue(reduit.sansJourFixe.isEmpty())
        // La colonne reste presente, meme videe : le planning garde ses sept jours.
        assertEquals(1, reduit.jours.size)
    }

    @Test
    fun `un planning qui ne contient que des scans est vide pour l application`() {
        val scan = SortiePlanning(titre = "Ordeal", slug = "ordeal", type = "Scans")
        val complet = Planning(
            jours = listOf(JourPlanning(JourSemaine.LUNDI, "21/09", listOf(scan)))
        )

        assertFalse(complet.estVide)
        assertTrue(complet.animesSeulement().estVide)
    }

    @Test
    fun `les libelles du site sont reconnus`() {
        assertEquals(JourSemaine.LUNDI, JourSemaine.depuisLibelle("Lundi"))
        assertEquals(JourSemaine.DIMANCHE, JourSemaine.depuisLibelle("DIMANCHE"))
        assertEquals(JourSemaine.MERCREDI, JourSemaine.depuisLibelle("  Mercredi  "))
        assertNull(JourSemaine.depuisLibelle("Lundimanche"))
        assertNull(JourSemaine.depuisLibelle(null))
    }

    @Test
    fun `le jour courant suit le calendrier du televiseur`() {
        assertEquals(JourSemaine.LUNDI, JourSemaine.courant(instant(jour = 21, heure = 12)))
        assertEquals(JourSemaine.DIMANCHE, JourSemaine.courant(instant(jour = 27, heure = 12)))
    }

    @Test
    fun `les jours precedents remontent du plus recent au plus ancien`() {
        val semaine = Planning(
            jours = JourSemaine.entries.map { jour ->
                JourPlanning(jour, "00/00", listOf(sortie(jour.name.lowercase())))
            }
        )

        val recents = semaine.sortiesDesJoursPrecedents(JourSemaine.JEUDI, profondeur = 3)

        assertEquals(listOf("mercredi", "mardi", "lundi"), recents.map { it.slug })
    }

    @Test
    fun `les jours precedents repassent par la fin de la semaine`() {
        val semaine = Planning(
            jours = JourSemaine.entries.map { jour ->
                JourPlanning(jour, "00/00", listOf(sortie(jour.name.lowercase())))
            }
        )

        // Lundi, les jours precedents sont ceux de la semaine ecoulee.
        val recents = semaine.sortiesDesJoursPrecedents(JourSemaine.LUNDI, profondeur = 2)

        assertEquals(listOf("dimanche", "samedi"), recents.map { it.slug })
    }

    @Test
    fun `une serie annoncee deux jours de suite n apparait qu une fois`() {
        val repetee = sortie("frieren")
        val semaine = Planning(
            jours = listOf(
                JourPlanning(JourSemaine.LUNDI, "00/00", listOf(repetee)),
                JourPlanning(JourSemaine.MARDI, "00/00", listOf(repetee, sortie("autre")))
            )
        )

        val recents = semaine.sortiesDesJoursPrecedents(JourSemaine.MERCREDI, profondeur = 3)

        assertEquals(listOf("frieren", "autre"), recents.map { it.slug })
    }

    @Test
    fun `le jour courant est exclu de ses propres jours precedents`() {
        val semaine = Planning(
            jours = listOf(JourPlanning(JourSemaine.MARDI, "00/00", listOf(sortie("mardi"))))
        )

        // Une profondeur superieure a la semaine ne doit pas ramener le jour lui-meme.
        val recents = semaine.sortiesDesJoursPrecedents(JourSemaine.MARDI, profondeur = 99)

        assertTrue(recents.isEmpty())
    }

    private fun sortie(slug: String) =
        SortiePlanning(titre = slug, slug = slug, type = "Anime")
}
