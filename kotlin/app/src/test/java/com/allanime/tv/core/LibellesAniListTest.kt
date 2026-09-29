package com.allanime.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibellesAniListTest {

    @Test
    fun `les formats connus sont traduits`() {
        assertEquals("Série TV", LibellesAniList.format("TV"))
        assertEquals("Film", LibellesAniList.format("MOVIE"))
        assertEquals("OAV", LibellesAniList.format("OVA"))
    }

    @Test
    fun `les statuts connus sont traduits`() {
        assertEquals("Terminé", LibellesAniList.statut("FINISHED"))
        assertEquals("En cours de diffusion", LibellesAniList.statut("RELEASING"))
    }

    @Test
    fun `les sources connues sont traduites`() {
        assertEquals("Light novel", LibellesAniList.source("LIGHT_NOVEL"))
        assertEquals("Création originale", LibellesAniList.source("ORIGINAL"))
    }

    @Test
    fun `une valeur inconnue est remise en forme sans etre inventee`() {
        assertEquals("Web comic", LibellesAniList.format("WEB_COMIC"))
        assertEquals("Quelque chose", LibellesAniList.statut("QUELQUE_CHOSE"))
    }

    @Test
    fun `une valeur absente ou vide ne produit rien`() {
        assertNull(LibellesAniList.format(null))
        assertNull(LibellesAniList.statut("   "))
        assertNull(LibellesAniList.saisonEtAnnee(null, null))
    }

    @Test
    fun `la saison se combine a l annee quand les deux sont connues`() {
        assertEquals("Printemps 2013", LibellesAniList.saisonEtAnnee("SPRING", 2013))
        assertEquals("Automne", LibellesAniList.saisonEtAnnee("FALL", null))
        assertEquals("2013", LibellesAniList.saisonEtAnnee(null, 2013))
    }

    @Test
    fun `le pays d origine est traduit depuis son code ISO`() {
        assertEquals("Japon", LibellesAniList.pays("JP"))
        assertEquals("Corée du Sud", LibellesAniList.pays("KR"))
    }
}
