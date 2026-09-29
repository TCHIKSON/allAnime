package com.allanime.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifie la politique de filtrage de la section 20 : bloquer ce qui est
 * explicitement publicitaire, laisser passer tout le reste.
 */
class AdBlockRulesTest {

    @Test
    fun `les hotes des lecteurs ne sont jamais bloques`() {
        listOf(
            "https://video.sibnet.ru/shell.php?videoid=4668080",
            "https://ansembed.net/embed-rjnzgaf5w0ws.html",
            "https://lpayer.embed4me.com/#avavy",
            "https://minochinos.com/embed/xyph6cycdxw3",
            "https://sendvid.com/embed/ytgsv65i",
            "https://s22.anime-sama.fr/videos/serie/ep01.mp4"
        ).forEach { url ->
            assertFalse(url, AdBlockRules.estBloquee(url))
        }
    }

    @Test
    fun `les regies publicitaires connues sont bloquees`() {
        listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://ad.doubleclick.net/ddm/track",
            "https://www.popads.net/pop.js",
            "https://main.exoclick.com/tag.js"
        ).forEach { url ->
            assertTrue(url, AdBlockRules.estBloquee(url))
        }
    }

    @Test
    fun `un domaine inconnu passe par defaut`() {
        // Les lecteurs dependent de CDN imprevisibles : l'inconnu n'est pas bloque.
        assertFalse(AdBlockRules.estBloquee("https://cdn-inconnu-42.example/chunk.js"))
        assertFalse(AdBlockRules.estBloquee("https://s7.cdn-video.example/segment-3.ts"))
    }

    @Test
    fun `les motifs de chemin publicitaires sont bloques`() {
        assertTrue(AdBlockRules.estBloquee("https://exemple.tld/ads/banner.js"))
        assertTrue(AdBlockRules.estBloquee("https://exemple.tld/static/popunder.min.js"))
    }

    @Test
    fun `une URL invalide ne bloque rien`() {
        assertFalse(AdBlockRules.estBloquee("pas une url"))
        assertFalse(AdBlockRules.estBloquee(""))
    }

    @Test
    fun `les coupures publicitaires de YouTube sont bloquees malgre l autorisation de l hote`() {
        // La bande-annonce et ses annonces sortent des memes hotes : seuls les
        // chemins les separent, et ceux-ci passent avant la liste des autorises.
        listOf(
            "https://www.youtube.com/pagead/interaction/?ai=xyz",
            "https://www.youtube.com/api/stats/ads?ver=2&ad_cpn=abc",
            "https://www.youtube.com/ptracking?video_id=abc",
            "https://www.youtube.com/get_midroll_info?video_id=abc",
            "https://www.youtube.com/youtubei/v1/player/ad_break?key=abc",
            "https://googleads.g.doubleclick.net/pagead/id"
        ).forEach { url -> assertTrue(url, AdBlockRules.estBloquee(url)) }
    }

    @Test
    fun `la lecture YouTube elle-meme reste autorisee`() {
        listOf(
            "https://www.youtube.com/iframe_api",
            "https://www.youtube.com/s/player/abc/base.js",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/youtubei/v1/player?key=abc",
            "https://rr3---sn-25ge7nz6.googlevideo.com/videoplayback?expire=1",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        ).forEach { url -> assertFalse(url, AdBlockRules.estBloquee(url)) }
    }

    @Test
    fun `le meme domaine est reconnu malgre les sous domaines`() {
        assertTrue(AdBlockRules.memeDomaine("video.sibnet.ru", "sibnet.ru"))
        assertTrue(AdBlockRules.memeDomaine("www.minochinos.com", "cdn.minochinos.com"))
        assertFalse(AdBlockRules.memeDomaine("sibnet.ru", "popads.net"))
        assertFalse(AdBlockRules.memeDomaine(null, "sibnet.ru"))
    }
}
