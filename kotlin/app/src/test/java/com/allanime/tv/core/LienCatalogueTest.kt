package com.allanime.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Extraction du slug des liens du site.
 *
 * Le segment repere est passe explicitement : le test ne doit pas dependre de la
 * valeur du secret `BASE_URL_2A`.
 */
class LienCatalogueTest {

    private fun slug(lien: String?) = LienCatalogue.slug(lien, segment = "catalogue")

    @Test
    fun `un lien absolu de la liste donne son slug`() {
        assertEquals("5-cm-per-second", slug("https://exemple.tld/catalogue/5-cm-per-second/"))
    }

    @Test
    fun `le nom d hote n a pas d importance`() {
        // Le site bascule de domaine sans prevenir : seul le chemin compte.
        assertEquals("akira", slug("https://un-autre-domaine.to/catalogue/akira"))
    }

    @Test
    fun `un lien du planning s arrete au slug`() {
        assertEquals("liar-game", slug("/catalogue/liar-game/saison1/vostfr/"))
    }

    @Test
    fun `un chemin deja relatif a la fiche est accepte`() {
        assertEquals("one-piece", slug("one-piece/scan/vf"))
    }

    @Test
    fun `la chaine de requete et l ancre sont ignorees`() {
        assertEquals("frieren", slug("/catalogue/frieren/?page=2#haut"))
    }

    @Test
    fun `un lien sans slug ne renvoie rien`() {
        assertNull(slug(null))
        assertNull(slug(""))
        assertNull(slug("/catalogue/"))
    }

    @Test
    fun `un lien absolu hors catalogue n est pas devine`() {
        // Sans le segment repere, le premier segment serait le nom d'hote.
        assertNull(slug("https://exemple.tld/planning"))
    }
}
