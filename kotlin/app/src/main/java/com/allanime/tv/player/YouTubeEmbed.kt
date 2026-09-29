package com.allanime.tv.player

import java.net.URI
import java.util.Locale

/**
 * Lecture d'une video YouTube **dans** l'application (bandes-annonces AniList).
 *
 * Charger `youtube.com/embed/<id>` comme document principal ne marche pas : sans
 * page parente, YouTube ne considere pas la lecture comme embarquee et propose
 * d'ouvrir la video sur son site au lieu de la jouer. C'est exactement ce que cette
 * classe evite.
 *
 * Le WebView recoit donc une page locale servie sous l'origine [ORIGINE], qui porte
 * un vrai `<iframe>` pilote par l'API IFrame de YouTube. Trois consequences :
 *
 *  - l'iframe a une page parente et une origine coherente, donc la video se joue
 *    sur place ;
 *  - `controls: 0` supprime l'habillage de YouTube — dont le lien « Regarder sur
 *    YouTube » — et laisse la barre de commandes de l'application seule a l'ecran ;
 *  - la page expose les memes fonctions `window.allAnime*` et le meme pont que les
 *    lecteurs d'episodes, si bien que l'ecran du lecteur n'a rien de particulier a
 *    faire pour une bande-annonce.
 *
 * Les coupures publicitaires, elles, sont refusees par [AdBlockRules] : video et
 * annonces sortant des memes hotes, ce sont leurs chemins qui les separent.
 */
object YouTubeEmbed {

    /** Origine sous laquelle la page locale est servie ; l'iframe l'exige. */
    const val ORIGINE = "https://www.youtube.com"

    /** Hote de [ORIGINE], reference des navigations autorisees pendant la lecture. */
    const val HOTE_ORIGINE = "www.youtube.com"

    /** Domaines reconnus, sous-domaines compris (`m.`, `www.`). */
    private val DOMAINES = listOf("youtube.com", "youtube-nocookie.com", "youtu.be")

    /** Un identifiant YouTube : onze caracteres du jeu utilise par le site. */
    private val IDENTIFIANT = Regex("^[A-Za-z0-9_-]{11}${'$'}")

    /**
     * Identifiant de la video portee par [url], ou `null` si ce n'est pas une adresse
     * YouTube. Les formes rencontrees sont reconnues : `/embed/<id>`, `watch?v=<id>`,
     * `youtu.be/<id>` et `/shorts/<id>`.
     *
     * `java.net.URI` plutot qu'`android.net.Uri` : la regle est verifiable hors
     * appareil, comme celles d'[AdBlockRules].
     */
    fun identifiant(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val hote = uri.host?.lowercase(Locale.ROOT) ?: return null
        val domaine = DOMAINES.firstOrNull { hote == it || hote.endsWith(".$it") }
            ?: return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val candidat = when {
            domaine == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "embed" -> segments.getOrNull(1)
            segments.firstOrNull() == "shorts" -> segments.getOrNull(1)
            else -> parametre(uri.rawQuery, "v")
        }

        return candidat?.takeIf { IDENTIFIANT.matches(it) }
    }

    /** `java.net.URI` ne lit pas la chaine de requete : elle se decoupe a la main. */
    private fun parametre(requete: String?, nom: String): String? =
        requete?.split('&')?.firstNotNullOfOrNull { paire ->
            val separateur = paire.indexOf('=')
            if (separateur > 0 && paire.substring(0, separateur) == nom) {
                paire.substring(separateur + 1)
            } else {
                null
            }
        }

    /** Page locale a charger sous [ORIGINE] pour lire la video [videoId]. */
    fun page(videoId: String): String = MODELE.replace(MARQUEUR, videoId)

    private const val MARQUEUR = "@@VIDEO@@"

    /**
     * Page hote de l'iframe. Le marqueur est remplace par l'identifiant de la video.
     *
     * `window.__allAnimeGuard` est pose des la premiere instruction : le script de
     * protection generique, injecte a la fin du chargement, se retire alors de
     * lui-meme et ne vient pas recouvrir les commandes definies ici, qui pilotent le
     * lecteur YouTube et non une balise `video`.
     */
    private val MODELE = """
        <!DOCTYPE html>
        <html lang='fr'>
        <head>
          <meta charset='utf-8'>
          <meta name='viewport' content='width=device-width, initial-scale=1'>
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%;
                         background:#000; overflow:hidden; }
            #lecteur, #lecteur iframe {
              position:absolute; top:0; left:0; right:0; bottom:0;
              width:100%; height:100%; border:0;
            }
            /* Voile transparent : rien de l'iframe n'est atteignable, donc aucun
               habillage de YouTube n'est actionnable par megarde. */
            #voile { position:absolute; top:0; left:0; right:0; bottom:0; z-index:2; }
          </style>
        </head>
        <body>
          <div id='lecteur'></div>
          <div id='voile'></div>
          <script>
            window.__allAnimeGuard = true;

            var pont = window.AllAnimeBridge || null;
            function prevenir(methode, a, b) {
              try {
                if (!pont || !pont[methode]) { return; }
                if (a === undefined) { pont[methode](); }
                else if (b === undefined) { pont[methode](a); }
                else { pont[methode](a, b); }
              } catch (e) {}
            }

            var lecteur = null;
            var demarre = false;
            var suivi = null;
            var garde = null;

            function entier(valeur) {
              var n = Math.floor(valeur || 0);
              return (isFinite(n) && n > 0) ? n : 0;
            }

            function rapporter() {
              if (!lecteur || !lecteur.getCurrentTime) { return; }
              var position = entier(lecteur.getCurrentTime());
              prevenir('onProgression', position, entier(lecteur.getDuration()));
              if (!demarre && position > 0) {
                demarre = true;
                if (garde) { clearTimeout(garde); garde = null; }
                prevenir('onLectureDemarree');
              }
            }

            window.allAnimeLire = function () {
              if (lecteur && lecteur.playVideo) { lecteur.playVideo(); }
            };
            window.allAnimePause = function () {
              if (lecteur && lecteur.pauseVideo) { lecteur.pauseVideo(); }
            };
            window.allAnimeBasculer = function () {
              if (!lecteur || !lecteur.getPlayerState) { return; }
              if (lecteur.getPlayerState() === 1) { lecteur.pauseVideo(); }
              else { lecteur.playVideo(); }
            };
            window.allAnimeAvancer = function (secondes) {
              if (!lecteur || !lecteur.seekTo) { return; }
              lecteur.seekTo(Math.max(0, lecteur.getCurrentTime() + secondes), true);
            };
            window.allAnimeAllerA = function (secondes) {
              if (!lecteur || !lecteur.seekTo) { return; }
              lecteur.seekTo(Math.max(0, secondes), true);
            };
            window.allAnimeChoisirQualite = function () {};

            window.onYouTubeIframeAPIReady = function () {
              lecteur = new YT.Player('lecteur', {
                videoId: '@@VIDEO@@',
                host: 'https://www.youtube-nocookie.com',
                playerVars: {
                  autoplay: 1, controls: 0, rel: 0, fs: 0, playsinline: 1,
                  disablekb: 1, iv_load_policy: 3, modestbranding: 1,
                  cc_load_policy: 0, origin: 'https://www.youtube.com'
                },
                events: {
                  onReady: function (evenement) {
                    prevenir('onVideoDetectee', true);
                    try { evenement.target.unMute(); } catch (e) {}
                    try { evenement.target.playVideo(); } catch (e) {}
                  },
                  onStateChange: function (evenement) {
                    if (evenement.data === 1) {
                      prevenir('onEtatLecture', true);
                      rapporter();
                    } else if (evenement.data === 2) {
                      prevenir('onEtatLecture', false);
                    } else if (evenement.data === 0) {
                      prevenir('onEtatLecture', false);
                      prevenir('onLectureTerminee');
                    }
                  },
                  /* 101 et 150 : le proprietaire de la video interdit la lecture
                     embarquee. Rien a tenter de plus, on le signale comme un echec
                     plutot que de renvoyer l'utilisateur hors de l'application. */
                  onError: function () { prevenir('onErreurLecture'); }
                }
              });

              suivi = setInterval(rapporter, 500);
              garde = setTimeout(function () {
                if (!demarre) { prevenir('onDemarrageImpossible'); }
              }, 12000);
            };

            var script = document.createElement('script');
            script.src = 'https://www.youtube.com/iframe_api';
            script.onerror = function () { prevenir('onErreurLecture'); };
            (document.head || document.documentElement).appendChild(script);
          </script>
        </body>
        </html>
    """.trimIndent()
}
