package com.allanime.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie la reconnaissance des adresses YouTube et la page qui les remplace.
 *
 * Une adresse non reconnue doit rendre `null` : le lecteur la chargerait alors
 * telle quelle, ce qui reste le bon comportement pour tout le reste du catalogue.
 */
class YouTubeEmbedTest {

    @Test
    fun `les formes d adresse connues donnent l identifiant`() {
        mapOf(
            "https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1" to "dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ" to "dQw4w9WgXcQ"
        ).forEach { (url, attendu) ->
            assertEquals(url, attendu, YouTubeEmbed.identifiant(url))
        }
    }

    @Test
    fun `une adresse etrangere a YouTube n est pas reconnue`() {
        listOf(
            "https://www.dailymotion.com/embed/video/x8abcde",
            "https://video.sibnet.ru/shell.php?videoid=4668080",
            "https://notyoutube.com/embed/dQw4w9WgXcQ",
            "pas une adresse"
        ).forEach { url -> assertNull(url, YouTubeEmbed.identifiant(url)) }
    }

    @Test
    fun `un identifiant mal forme est refuse plutot que devine`() {
        assertNull(YouTubeEmbed.identifiant("https://www.youtube.com/embed/trop-court"))
        assertNull(YouTubeEmbed.identifiant("https://www.youtube.com/watch?v=avec.point"))
        assertNull(YouTubeEmbed.identifiant("https://www.youtube.com/watch?t=30"))
        assertNull(YouTubeEmbed.identifiant("https://www.youtube.com/embed/"))
    }

    @Test
    fun `la page porte l identifiant demande`() {
        val page = YouTubeEmbed.page("dQw4w9WgXcQ")

        assertTrue(page.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue("aucun marqueur ne doit subsister", !page.contains("@@VIDEO@@"))
    }

    @Test
    fun `la page neutralise le script de protection generique`() {
        // Sans ce drapeau, le script injecte a la fin du chargement redefinirait les
        // commandes pour une balise video inexistante ici.
        assertTrue(YouTubeEmbed.page("dQw4w9WgXcQ").contains("window.__allAnimeGuard = true"))
    }

    @Test
    fun `la page cache l habillage de YouTube`() {
        val page = YouTubeEmbed.page("dQw4w9WgXcQ")

        // controls: 0 retire la barre de YouTube, dont le lien vers son site.
        assertTrue(page.contains("controls: 0"))
        assertTrue(page.contains("id='voile'"))
    }

    @Test
    fun `la page expose les memes commandes que les autres lecteurs`() {
        val page = YouTubeEmbed.page("dQw4w9WgXcQ")

        listOf(
            "window.allAnimeLire",
            "window.allAnimePause",
            "window.allAnimeBasculer",
            "window.allAnimeAvancer",
            "window.allAnimeAllerA"
        ).forEach { commande -> assertTrue(commande, page.contains(commande)) }
    }
}
