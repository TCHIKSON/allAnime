package com.allanime.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifie les regles de slugification demandees par la section 32. */
class SlugifierTest {

    @Test
    fun `exemple du cahier des charges`() {
        assertEquals("classroom-of-the-elite", Slugifier.slugify("Classroom of the Elite"))
    }

    @Test
    fun `les accents sont retires`() {
        assertEquals("lete-ou-elle-revait", Slugifier.slugify("L'été où elle rêvait"))
    }

    @Test
    fun `les apostrophes disparaissent sans creer de tiret`() {
        assertEquals("grimms-notes", Slugifier.slugify("Grimm's Notes"))
    }

    @Test
    fun `espaces multiples et bords sont absorbes`() {
        assertEquals("attack-on-titan", Slugifier.slugify("   Attack   on    Titan   "))
    }

    @Test
    fun `ponctuation et symboles deviennent un tiret unique`() {
        assertEquals("re-zero-starting-life", Slugifier.slugify("Re:Zero -- Starting Life!"))
        assertEquals("hunter-hunter-2011", Slugifier.slugify("HUNTER×HUNTER (2011)"))
    }

    @Test
    fun `les chiffres sont conserves`() {
        assertEquals("86-eighty-six", Slugifier.slugify("86 EIGHTY-SIX"))
    }

    @Test
    fun `une entree vide ou nulle donne une chaine vide`() {
        assertEquals("", Slugifier.slugify(null))
        assertEquals("", Slugifier.slugify("   "))
        assertEquals("", Slugifier.slugify("!!!"))
    }

    @Test
    fun `la slugification est deterministe`() {
        val titre = "Fruits Basket: The Final Season"
        assertEquals(Slugifier.slugify(titre), Slugifier.slugify(titre))
    }

    @Test
    fun `la forme separatrice traite l apostrophe comme la ponctuation`() {
        assertEquals("grimm-s-notes", Slugifier.slugifySeparateur("Grimm's Notes"))
        assertEquals("l-ete-ou-elle-revait", Slugifier.slugifySeparateur("L'été où elle rêvait"))
    }

    @Test
    fun `la forme separatrice ne change rien sans apostrophe`() {
        val titre = "Classroom of the Elite"
        assertEquals(Slugifier.slugify(titre), Slugifier.slugifySeparateur(titre))
    }

    @Test
    fun `les variantes donnent les deux formes quand elles different`() {
        assertEquals(
            listOf("grimms-notes", "grimm-s-notes"),
            Slugifier.variantes("Grimm's Notes")
        )
    }

    @Test
    fun `les variantes se reduisent a une seule sans apostrophe`() {
        assertEquals(listOf("attack-on-titan"), Slugifier.variantes("Attack on Titan"))
    }

    @Test
    fun `les variantes d une entree vide sont vides`() {
        assertEquals(emptyList<String>(), Slugifier.variantes(null))
        assertEquals(emptyList<String>(), Slugifier.variantes("!!!"))
    }

    @Test
    fun `une apostrophe en bord ne laisse pas de tiret`() {
        assertEquals("tis-time-for-torture", Slugifier.slugifySeparateur("'Tis Time for Torture"))
    }
}
