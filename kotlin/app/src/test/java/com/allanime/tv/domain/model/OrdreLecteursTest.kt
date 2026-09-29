package com.allanime.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ordre des tentatives de lecteurs : ce qui a fonctionne passe devant, le rang du
 * catalogue ne departage que les hebergeurs inconnus.
 */
class OrdreLecteursTest {

    private fun lecteurs(vararg hotes: String): List<Lecteur> =
        hotes.mapIndexed { index, hote -> Lecteur(index + 1, "https://$hote/embed/xyz") }

    @Test
    fun `sans historique l ordre du catalogue est conserve`() {
        val liste = lecteurs("sibnet.ru", "ansembed.xyz", "vidmoly.to")

        assertEquals(listOf(0, 1, 2), OrdreLecteurs.ordonner(liste, emptyMap()))
    }

    @Test
    fun `un hebergeur qui a echoue passe en dernier`() {
        val liste = lecteurs("sibnet.ru", "ansembed.xyz", "vidmoly.to")

        val ordre = OrdreLecteurs.ordonner(liste, mapOf("sibnet" to -2))

        assertEquals(listOf(1, 2, 0), ordre)
    }

    @Test
    fun `un hebergeur qui a fonctionne passe en premier`() {
        val liste = lecteurs("sibnet.ru", "ansembed.xyz", "vidmoly.to")

        val ordre = OrdreLecteurs.ordonner(liste, mapOf("vidmoly" to 3))

        assertEquals(listOf(2, 0, 1), ordre)
    }

    @Test
    fun `les scores se combinent, les inconnus restent au milieu`() {
        val liste = lecteurs("sibnet.ru", "ansembed.xyz", "vidmoly.to")

        val ordre = OrdreLecteurs.ordonner(
            liste,
            mapOf("sibnet" to -3, "vidmoly" to 2)
        )

        // vidmoly (+2), ansembed (inconnu, 0), sibnet (-3)
        assertEquals(listOf(2, 1, 0), ordre)
    }

    @Test
    fun `a score egal le rang du catalogue tranche`() {
        val liste = lecteurs("a.com", "b.com", "c.com")

        val ordre = OrdreLecteurs.ordonner(liste, mapOf("a" to 1, "c" to 1))

        assertEquals(listOf(0, 2, 1), ordre)
    }

    @Test
    fun `une liste vide ne casse rien`() {
        assertEquals(emptyList<Int>(), OrdreLecteurs.ordonner(emptyList(), mapOf("a" to 1)))
    }
}
