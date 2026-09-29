package com.allanime.tv.player

import java.net.URI
import java.util.Locale

/**
 * Regles de filtrage reseau appliquees par le WebView (sections 15 et 20 du cahier
 * des charges).
 *
 * Principe : **par defaut on laisse passer**. Une requete inconnue n'est jamais
 * bloquee, car les lecteurs dependent de domaines de CDN imprevisibles. Seuls sont
 * refuses les hotes publicitaires explicitement listes et quelques motifs de chemin
 * sans ambiguite.
 *
 * Les listes sont volontairement declaratives : ajouter ou retirer une regle ne
 * demande aucune modification de la logique du lecteur.
 */
object AdBlockRules {

    /**
     * Points d'entree publicitaires sans ambiguite, refuses **avant meme** la liste
     * des hotes autorises.
     *
     * C'est le seul moyen de retirer les coupures publicitaires d'une bande-annonce
     * YouTube : la video et ses annonces sortent des memes hotes — `youtube.com`,
     * `googlevideo.com` — qu'il faut donc garder autorises. Ce sont les chemins qui
     * les separent, et ceux-ci ne servent qu'a la publicite ou a sa mesure.
     *
     * Volontairement etroits : `/youtubei/v1/player` porte la lecture elle-meme et
     * n'a rien a faire ici, seul son point d'arret publicitaire est vise.
     */
    private val MOTIFS_PUBLICITAIRES_ABSOLUS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/youtubei/v1/player/ad_break",
        "/pcs/activeview",
        "/activeview?",
        "/adformat"
    )

    /**
     * Hotes des lecteurs et de leurs dependances : toujours autorises, sauf sur les
     * chemins de [MOTIFS_PUBLICITAIRES_ABSOLUS]. Cette liste gagne sur les autres.
     */
    private val HOTES_AUTORISES = listOf(
        "sibnet.ru",
        "embed4me.com",
        "ansembed.net",
        "minochinos.com",
        "vidmoly.to",
        "vidmoly.me",
        "sendvid.com",
        "dingtezuni.com",
        "vk.com",
        "userapi.com",
        "youtube.com",
        "youtube-nocookie.com",
        "ytimg.com",
        "googlevideo.com",
        "dailymotion.com",
        "dmcdn.net"
    )

    /** Regies publicitaires et reseaux de popunder connus. */
    private val DOMAINES_PUBLICITAIRES = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagservices.com",
        "adservice.google.com",
        "adnxs.com",
        "adsterra.com",
        "adcash.com",
        "clickadu.com",
        "criteo.com",
        "criteo.net",
        "exoclick.com",
        "exosrv.com",
        "hilltopads.net",
        "juicyads.com",
        "mgid.com",
        "onclickads.net",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "poweredby.jads.co",
        "propellerads.com",
        "pubmatic.com",
        "revcontent.com",
        "rubiconproject.com",
        "smartadserver.com",
        "taboola.com",
        "trafficjunky.net",
        "zedo.com"
    )

    /**
     * Motifs de chemin sans ambiguite. Volontairement etroits : un simple `/ad`
     * declencherait des faux positifs sur des ressources legitimes.
     */
    private val MOTIFS_CHEMIN = listOf(
        "/ads/",
        "/adserver",
        "/ad-frame",
        "/popunder",
        "/popad",
        "adsbygoogle",
        "prebid",
        "gpt.js"
    )

    /** `true` si la requete doit etre refusee par `shouldInterceptRequest()`. */
    fun estBloquee(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val hote = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false

        val chemin = (uri.path.orEmpty() + "?" + uri.query.orEmpty()).lowercase(Locale.ROOT)
        if (MOTIFS_PUBLICITAIRES_ABSOLUS.any { chemin.contains(it) }) return true

        if (HOTES_AUTORISES.any { hote == it || hote.endsWith(".$it") }) return false
        if (DOMAINES_PUBLICITAIRES.any { hote == it || hote.endsWith(".$it") }) return true

        return MOTIFS_CHEMIN.any { chemin.contains(it) }
    }

    /**
     * `true` si l'hote appartient au meme domaine enregistrable que celui du lecteur
     * charge : sert a distinguer une navigation legitime d'une redirection publicitaire.
     */
    fun memeDomaine(hoteA: String?, hoteB: String?): Boolean {
        val a = domaineRacine(hoteA) ?: return false
        val b = domaineRacine(hoteB) ?: return false
        return a == b
    }

    private fun domaineRacine(hote: String?): String? {
        val propre = hote?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        val labels = propre.split('.')
        return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else propre
    }
}
