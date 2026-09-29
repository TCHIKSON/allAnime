package com.allanime.tv.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inventaire des qualites : lecture du JSON produit par le script injecte, et regles
 * de presentation. Le script est du JavaScript non teste par la JVM, donc c'est bien
 * son format de sortie qui est fige ici.
 */
class QualiteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `un inventaire hls est decode avec ses hauteurs`() {
        val inventaire = json.decodeFromString<InventaireQualites>(
            """
            {"actif":"hls:2","hauteur":720,"entrees":[
              {"id":"auto","hauteur":0,"libelle":null},
              {"id":"hls:0","hauteur":480,"libelle":null},
              {"id":"hls:1","hauteur":720,"libelle":null},
              {"id":"hls:2","hauteur":1080,"libelle":null}
            ]}
            """.trimIndent()
        )

        assertEquals("hls:2", inventaire.actif)
        assertEquals(720, inventaire.hauteurCourante)
        assertEquals(4, inventaire.entrees.size)
        assertFalse(inventaire.estFigee)
    }

    @Test
    fun `une clef inconnue n invalide pas l inventaire`() {
        val inventaire = json.decodeFromString<InventaireQualites>(
            """{"actif":"auto","entrees":[],"hauteur":0,"nouveauChamp":"peu importe"}"""
        )

        assertEquals(QualiteVideo.ID_AUTO, inventaire.actif)
        assertTrue(inventaire.estFigee)
    }

    @Test
    fun `une page sans choix est declaree figee`() {
        val inventaire = InventaireQualites(
            actif = QualiteVideo.ID_AUTO,
            entrees = listOf(QualiteVideo(QualiteVideo.ID_AUTO)),
            hauteurCourante = 1080
        )

        assertTrue(inventaire.estFigee)
        assertEquals(emptyList<QualiteVideo>(), inventaire.entreesTriees)
    }

    @Test
    fun `les entrees sont triees de la plus fine a la plus grossiere`() {
        val inventaire = InventaireQualites(
            entrees = listOf(
                QualiteVideo(QualiteVideo.ID_AUTO),
                QualiteVideo("hls:0", hauteur = 480),
                QualiteVideo("hls:1", hauteur = 1080),
                QualiteVideo("hls:2", hauteur = 720)
            )
        )

        assertEquals(
            listOf("auto", "hls:1", "hls:2", "hls:0"),
            inventaire.entreesTriees.map { it.id }
        )
    }

    @Test
    fun `deux debits pour la meme resolution ne sont proposes qu une fois`() {
        val inventaire = InventaireQualites(
            entrees = listOf(
                QualiteVideo("hls:0", hauteur = 1080),
                QualiteVideo("hls:1", hauteur = 1080),
                QualiteVideo("hls:2", hauteur = 720)
            )
        )

        assertEquals(listOf("auto", "hls:0", "hls:2"), inventaire.entreesTriees.map { it.id })
    }

    @Test
    fun `l entree automatique est toujours en tete`() {
        val inventaire = InventaireQualites(
            entrees = listOf(QualiteVideo("src:0", hauteur = 360))
        )

        assertTrue(inventaire.entreesTriees.first().estAutomatique)
    }

    @Test
    fun `le niveau auto declare par la page ne fait pas doublon`() {
        // hls.js expose parfois un niveau nomme « Auto » a cote des resolutions.
        val inventaire = InventaireQualites(
            entrees = listOf(
                QualiteVideo("hls:0", hauteur = 1080),
                QualiteVideo("hls:1", hauteur = 480),
                QualiteVideo("hls:2", hauteur = 0, libelle = "Auto")
            )
        )

        assertEquals(listOf("auto", "hls:0", "hls:1"), inventaire.entreesTriees.map { it.id })
    }

    @Test
    fun `un lecteur qui n annonce que son propre auto est declare fige`() {
        val inventaire = InventaireQualites(
            entrees = listOf(QualiteVideo("hls:0", hauteur = 0, libelle = "AUTO"))
        )

        assertTrue(inventaire.estFigee)
    }

    @Test
    fun `un libelle non numerique est conserve`() {
        val inventaire = InventaireQualites(
            entrees = listOf(QualiteVideo("src:0", hauteur = 0, libelle = "HD"))
        )

        assertEquals("HD", inventaire.entreesTriees.last().libelle)
    }
}
