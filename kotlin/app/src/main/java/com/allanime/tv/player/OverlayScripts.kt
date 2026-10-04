package com.allanime.tv.player

/**
 * CSS et JavaScript injectes dans les pages de lecteurs (sections 23 et 24 du cahier
 * des charges).
 *
 * Tout est centralise ici pour pouvoir ajuster les regles sans toucher au lecteur.
 * Deux garde-fous permanents :
 *  - aucun element contenant une `video` ou un `iframe` n'est masque, afin de ne
 *    jamais faire disparaitre le lecteur ni ses controles ;
 *  - l'injection est idempotente, ce qui permet de la rejouer apres une navigation
 *    interne sans empiler les observateurs.
 *
 * Limite connue et assumee : un WebView n'execute ces scripts que dans le document
 * principal. Si le lecteur est enferme dans un iframe d'une autre origine, le
 * nettoyage des overlays, le choix de la qualite et les commandes de lecture ne s'y
 * appliquent pas ; c'est le filtrage reseau ([AdBlockRules]) et le choix d'un autre
 * lecteur qui prennent alors le relais.
 */
internal object OverlayScripts {

    /** Nom de l'objet Java expose aux pages. */
    const val NOM_PONT = "AllAnimeBridge"

    /** Selecteurs publicitaires classiques, masques des le chargement. */
    private const val CSS_ANTI_OVERLAY = """
        ins.adsbygoogle,
        iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"],
        iframe[src*="popunder"],
        div[id^="ad_"],
        div[id^="ads_"],
        div[class^="ads-"],
        div[class*="adsbox"],
        div[id*="adsbox"],
        a[href*="utm_medium=popunder"] {
            display: none !important;
            visibility: hidden !important;
            pointer-events: none !important;
        }
    """

    /**
     * Script de protection : neutralisation des fenetres parasites, nettoyage des
     * overlays (y compris ceux qui apparaissent apres le chargement), detection de la
     * video, verdict sur le demarrage effectif de la lecture et inventaire des
     * qualites disponibles.
     */
    val JS_PROTECTION: String = """
        (function () {
          if (window.__allAnimeGuard) { return; }
          window.__allAnimeGuard = true;

          /* Nombre de sondages avant de conclure qu'aucune video n'arrivera. */
          var ESSAIS_MAX = 14;

          /* Delai laisse a la source pour produire ses premieres images. Au-dela, le
             lecteur est considere comme injouable ou trop lent, et l'application
             bascule sur le suivant sans intervention de l'utilisateur. */
          var DELAI_DEMARRAGE_MS = 12000;

          /* Seuil au-dela duquel on considere que des images ont bien ete affichees. */
          var SEUIL_DEMARRAGE_S = 0.6;

          var pont = window.${NOM_PONT} || null;
          /* Le pont Java expose des methodes sans argument et d'autres avec : on
             appelle donc avec l'arite exacte, sinon le WebView ne trouve pas la
             methode correspondante. */
          function prevenir(methode, valeur) {
            try {
              if (!pont || !pont[methode]) { return; }
              if (valeur === undefined) { pont[methode](); } else { pont[methode](valeur); }
            } catch (e) {}
          }

          /* --- 0. Cadres accessibles ----------------------------------------- */
          /* Un WebView n'execute ce script que dans le document principal. Les cadres
             de **meme origine** restent lisibles depuis ici, et beaucoup de pages
             d'integration ne sont qu'une coquille autour d'un second cadre, du meme
             site, qui porte le vrai lecteur : sans les parcourir, on conclurait qu'il
             n'y a pas de video la ou elle est simplement un etage plus bas.

             Un cadre d'une autre origine, lui, leve des qu'on le touche. C'est la
             limite de la plateforme, et [cadreExterne] en tire les consequences plutot
             que de laisser croire a un lecteur mort. */
          var MAX_DOCUMENTS = 8;

          function documentsAccessibles() {
            var docs = [document];
            for (var i = 0; i < docs.length && docs.length < MAX_DOCUMENTS; i++) {
              var cadres;
              try { cadres = docs[i].querySelectorAll('iframe, frame'); } catch (e) { continue; }
              for (var j = 0; j < cadres.length; j++) {
                try {
                  var interne = cadres[j].contentDocument;
                  if (interne && interne.querySelector) { docs.push(interne); }
                } catch (e) {}
              }
            }
            return docs;
          }

          function trouverVideo() {
            var docs = documentsAccessibles();
            for (var i = 0; i < docs.length; i++) {
              try {
                var video = docs[i].querySelector('video');
                if (video) { return video; }
              } catch (e) {}
            }
            return null;
          }

          /* Adresse du premier cadre d'une autre origine assez grand pour porter le
             lecteur : une banniere publicitaire n'occupe jamais le quart de l'ecran. */
          function cadreExterne() {
            var ecran = window.innerWidth * window.innerHeight;
            if (ecran <= 0) { return null; }

            var docs = documentsAccessibles();
            for (var i = 0; i < docs.length; i++) {
              var cadres;
              try { cadres = docs[i].querySelectorAll('iframe'); } catch (e) { continue; }
              for (var j = 0; j < cadres.length; j++) {
                var cadre = cadres[j];
                var lisible = false;
                try { lisible = !!cadre.contentDocument; } catch (e) { lisible = false; }
                if (lisible) { continue; }

                var adresse = cadre.getAttribute('src') || cadre.getAttribute('data-src') || '';
                if (adresse.indexOf('http') !== 0 && adresse.indexOf('//') !== 0) { continue; }

                var cadrage = cadre.getBoundingClientRect();
                if ((cadrage.width * cadrage.height) / ecran > 0.25) { return adresse; }
              }
            }
            return null;
          }

          /* --- 0 bis. Appui simule ------------------------------------------- */
          /* Certaines pages ne lancent rien avant un clic : coupure publicitaire a
             passer, couche « cliquez pour lire », lecteur qui ne cree sa balise video
             qu'au premier geste. Aucun script ne peut franchir cela dans un cadre
             d'une autre origine — un vrai appui, lui, le peut, et c'est l'application
             qui le produit sur le WebView.

             Il n'est demande que la ou l'on s'appretait a declarer le lecteur mort :
             une page qui demarre seule n'en recoit jamais, et les lecteurs qui
             fonctionnent deja ne changent pas de comportement. */
          var APPUIS_MAX = 2;
          var appuisDemandes = 0;

          function demanderAppui() {
            if (appuisDemandes >= APPUIS_MAX) { return false; }
            appuisDemandes++;
            prevenir('onAppuiRequis');
            return true;
          }

          /* --- 1. CSS anti-overlay ------------------------------------------- */
          try {
            var style = document.createElement('style');
            style.type = 'text/css';
            style.appendChild(document.createTextNode(${cssLitteral()}));
            (document.head || document.documentElement).appendChild(style);
          } catch (e) {}

          /* --- 2. Fenetres parasites et redirections ------------------------- */
          try {
            window.open = function () { prevenir('onPopupBloque'); return null; };
          } catch (e) {}

          document.addEventListener('click', function (evenement) {
            var noeud = evenement.target;
            while (noeud && noeud !== document.body) {
              if (noeud.tagName === 'A' && noeud.target && noeud.target !== '_self') {
                noeud.target = '_self';
              }
              noeud = noeud.parentNode;
            }
          }, true);

          /* --- 3. Overlays dynamiques ---------------------------------------- */
          function contientLecteur(element) {
            if (!element) { return false; }
            if (element.tagName === 'VIDEO' || element.tagName === 'IFRAME') { return true; }
            return !!(element.querySelector && element.querySelector('video, iframe'));
          }

          function estOverlay(element) {
            /* La mesure se fait dans la fenetre du document de l'element : celui-ci
               peut venir d'un cadre, dont les dimensions ne sont pas les notres. */
            var fenetre = (element.ownerDocument && element.ownerDocument.defaultView) || window;
            var style = fenetre.getComputedStyle ? fenetre.getComputedStyle(element) : null;
            if (!style) { return false; }
            if (style.position !== 'fixed' && style.position !== 'absolute') { return false; }
            var couche = parseInt(style.zIndex, 10);
            if (isNaN(couche) || couche < 1000) { return false; }
            var cadre = element.getBoundingClientRect();
            var ecran = fenetre.innerWidth * fenetre.innerHeight;
            if (ecran <= 0) { return false; }
            return (cadre.width * cadre.height) / ecran > 0.35;
          }

          function nettoyer() {
            var docs = documentsAccessibles();
            var masques = 0;
            for (var d = 0; d < docs.length; d++) {
              var corps = docs[d].body;
              if (!corps) { continue; }
              var candidats;
              try {
                candidats = corps.querySelectorAll('div, span, a, ins, aside, section');
              } catch (e) { continue; }
              for (var i = 0; i < candidats.length; i++) {
                var element = candidats[i];
                if (contientLecteur(element)) { continue; }
                if (estOverlay(element)) {
                  element.style.setProperty('display', 'none', 'important');
                  masques++;
                }
              }
            }
            if (masques > 0) { prevenir('onOverlaysMasques', masques); }
          }

          /* --- 4. Verdict sur le demarrage de la lecture ---------------------- */
          /* Un lecteur n'est declare bon que lorsque des images defilent reellement.
             C'est la seule mesure fiable de « chargement fluide » : une page qui
             repond, une balise video presente et meme un evenement `play` ne
             garantissent rien si la source ne delivre jamais d'image. */
          var demarrageConfirme = false;
          var echecSignale = false;
          var pauseDemandee = false;
          var minuterieDemarrage = null;

          function armerDemarrage(video) {
            if (minuterieDemarrage !== null) { return; }
            minuterieDemarrage = setTimeout(function () {
              minuterieDemarrage = null;
              if (demarrageConfirme || echecSignale || pauseDemandee) { return; }
              if ((video.currentTime || 0) > SEUIL_DEMARRAGE_S) { return; }

              /* Rien n'a defile. Avant de condamner ce lecteur, on lui donne le geste
                 qu'il attend peut-etre — couche a ecarter, publicite a passer — et un
                 second delai. Sans ce sursis, une page a coupure publicitaire etait
                 declaree injouable alors qu'elle n'avait jamais ete touchee. */
              if (demanderAppui()) {
                armerDemarrage(video);
                return;
              }

              echecSignale = true;
              prevenir('onDemarrageImpossible');
            }, DELAI_DEMARRAGE_MS);
          }

          function confirmerDemarrage() {
            if (demarrageConfirme) { return; }
            demarrageConfirme = true;
            if (minuterieDemarrage !== null) {
              clearTimeout(minuterieDemarrage);
              minuterieDemarrage = null;
            }
            prevenir('onLectureDemarree');
          }

          /* --- 5. Qualites disponibles --------------------------------------- */
          /* Trois sources d'information possibles, testees dans cet ordre. Aucune
             qualite n'est inventee : si rien n'est lisible, la liste ne contient que
             l'entree automatique et l'application le dit clairement. */
          function instanceHls() {
            var candidats = [];
            candidats.push(window.hls);
            candidats.push(window.hlsPlayer);
            if (window.Hls) { candidats.push(window.Hls.instance); }
            if (window.player) { candidats.push(window.player.hls); }
            if (videoSuivie) { candidats.push(videoSuivie.hls); }
            for (var i = 0; i < candidats.length; i++) {
              var candidat = candidats[i];
              if (candidat && candidat.levels && candidat.levels.length > 0) { return candidat; }
            }
            return null;
          }

          function hauteurCourante() {
            var video = trouverVideo();
            if (!video) { return 0; }
            return video.videoHeight || 0;
          }

          function entree(id, hauteur, libelle) {
            return { id: id, hauteur: hauteur || 0, libelle: libelle || null };
          }

          function inventaireQualites() {
            var entrees = [entree('auto', 0, null)];

            var hls = instanceHls();
            if (hls) {
              for (var i = 0; i < hls.levels.length; i++) {
                entrees.push(entree('hls:' + i, hls.levels[i].height, hls.levels[i].name));
              }
              var niveau = hls.currentLevel;
              var actifHls = (niveau !== null && niveau !== undefined && niveau >= 0)
                ? 'hls:' + niveau
                : 'auto';
              return { actif: actifHls, entrees: entrees, hauteur: hauteurCourante() };
            }

            if (window.jwplayer) {
              try {
                var jw = window.jwplayer();
                var niveaux = jw.getQualityLevels ? jw.getQualityLevels() : null;
                if (niveaux && niveaux.length > 1) {
                  for (var j = 0; j < niveaux.length; j++) {
                    entrees.push(entree('jw:' + j, niveaux[j].height, niveaux[j].label));
                  }
                  var courant = jw.getCurrentQuality ? jw.getCurrentQuality() : 0;
                  return {
                    actif: courant > 0 ? 'jw:' + courant : 'auto',
                    entrees: entrees,
                    hauteur: hauteurCourante()
                  };
                }
              } catch (e) {}
            }

            var video = trouverVideo();
            var sources = video ? video.querySelectorAll('source') : [];
            if (sources.length > 1) {
              for (var k = 0; k < sources.length; k++) {
                var source = sources[k];
                var brut = source.getAttribute('label') || source.getAttribute('title') ||
                  source.getAttribute('res') || source.getAttribute('size') ||
                  source.getAttribute('data-res');
                var hauteur = 0;
                var mesure = /(\d{3,4})\s*p/i.exec(brut || '') ||
                  /(\d{3,4})\s*p/i.exec(source.getAttribute('src') || '');
                if (mesure) { hauteur = parseInt(mesure[1], 10); }
                else if (brut && /^\d{3,4}${'$'}/.test(brut)) { hauteur = parseInt(brut, 10); }
                entrees.push(entree('src:' + k, hauteur, brut));
              }
              return { actif: qualiteChoisie, entrees: entrees, hauteur: hauteurCourante() };
            }

            /* Rien d'enumerable : seule la resolution reellement affichee est connue. */
            return { actif: 'auto', entrees: [entree('auto', 0, null)], hauteur: hauteurCourante() };
          }

          var qualiteChoisie = 'auto';
          var dernierInventaire = '';

          function signalerQualites() {
            try {
              var texte = JSON.stringify(inventaireQualites());
              if (texte === dernierInventaire) { return; }
              dernierInventaire = texte;
              prevenir('onQualites', texte);
            } catch (e) {}
          }

          window.allAnimeChoisirQualite = function (id) {
            var video = trouverVideo();
            try {
              var hls = instanceHls();
              if (hls && (id === 'auto' || id.indexOf('hls:') === 0)) {
                hls.currentLevel = (id === 'auto') ? -1 : parseInt(id.substring(4), 10);
                qualiteChoisie = id;
                signalerQualites();
                return;
              }

              if (id.indexOf('jw:') === 0 && window.jwplayer) {
                window.jwplayer().setCurrentQuality(parseInt(id.substring(3), 10));
                qualiteChoisie = id;
                signalerQualites();
                return;
              }

              if (id.indexOf('src:') === 0 && video) {
                var sources = video.querySelectorAll('source');
                var choix = sources[parseInt(id.substring(4), 10)];
                if (!choix) { return; }

                /* Changer de source remet la video a zero : on memorise l'instant et
                   l'etat de lecture pour les restaurer une fois les metadonnees la. */
                var instant = video.currentTime || 0;
                var lisait = !video.paused;
                video.src = choix.src || choix.getAttribute('src');
                video.load();
                video.addEventListener('loadedmetadata', function repositionner() {
                  video.removeEventListener('loadedmetadata', repositionner);
                  try { video.currentTime = instant; } catch (e) {}
                  if (lisait) { try { video.play(); } catch (e) {} }
                });
                qualiteChoisie = id;
              }
            } catch (e) {}
            signalerQualites();
          };

          /* --- 6. Video : detection, autoplay, fin de lecture ----------------- */
          var videoSuivie = null;
          function brancherVideo() {
            var video = trouverVideo();
            if (!video) { return false; }
            if (video === videoSuivie) { return true; }
            videoSuivie = video;
            prevenir('onVideoDetectee', true);
            video.addEventListener('ended', function () { prevenir('onLectureTerminee'); });
            /* Une source illisible n'emet aucune erreur reseau au niveau du WebView :
               c'est la balise video qui le signale. Sans cela, la bascule vers le
               lecteur suivant ne se declencherait jamais. */
            video.addEventListener('error', function () { prevenir('onErreurLecture'); });

            /* Position de lecture : alimente la barre de progression et l'historique
               « Reprendre votre visionnage ». Limite a un envoi toutes les deux
               secondes pour ne pas saturer le pont JavaScript. */
            function rapporter() {
              try {
                if (!pont || !pont.onProgression) { return; }
                var duree = isFinite(video.duration) ? Math.floor(video.duration) : 0;
                pont.onProgression(Math.floor(video.currentTime || 0), duree);
              } catch (e) {}
            }

            var dernierRapport = 0;
            video.addEventListener('timeupdate', function () {
              if ((video.currentTime || 0) > SEUIL_DEMARRAGE_S) { confirmerDemarrage(); }
              var maintenant = Date.now();
              if (maintenant - dernierRapport < 2000) { return; }
              dernierRapport = maintenant;
              rapporter();
            });
            video.addEventListener('loadedmetadata', function () {
              rapporter();
              signalerQualites();
            });
            video.addEventListener('seeked', rapporter);
            /* L'etat reel de lecture pilote le bouton Lire / Pause de l'application. */
            video.addEventListener('play', function () { prevenir('onEtatLecture', true); });
            video.addEventListener('playing', function () {
              prevenir('onEtatLecture', true);
              signalerQualites();
            });
            video.addEventListener('pause', function () { prevenir('onEtatLecture', false); });

            /* Une promesse play() rejetee ne signifie pas que l'autoplay a echoue :
               elle est frequemment interrompue par la page elle-meme, qui relance sa
               propre lecture. On ne conclut qu'apres avoir verifie l'etat reel. */
            function verifierAutoplay() {
              setTimeout(function () {
                if (!video.paused || pauseDemandee) { return; }
                /* readyState >= 3 : les images sont pretes, la pause vient donc bien
                   d'un refus de la page. En dessous, c'est la source qui ne se charge
                   pas : le chien de garde de demarrage tranchera, sans quoi on
                   signalerait un faux refus d'autoplay. */
                if (video.readyState < 3) { return; }
                try { video.play(); } catch (e) {}
                setTimeout(function () {
                  if (video.paused && !pauseDemandee) { prevenir('onAutoplayRefuse'); }
                }, 1200);
              }, 2000);
            }

            try {
              var promesse = video.play();
              if (promesse && promesse['catch']) {
                promesse['catch'](verifierAutoplay);
              }
            } catch (e) { verifierAutoplay(); }

            armerDemarrage(video);
            /* Les niveaux HLS n'existent qu'apres l'analyse du manifeste, et les
               menus des lecteurs tiers se construisent parfois plus tard encore. */
            setTimeout(signalerQualites, 1000);
            setTimeout(signalerQualites, 3000);
            setTimeout(signalerQualites, 6000);
            signalerQualites();
            return true;
          }

          /* --- 7. Surveillance du DOM ---------------------------------------- */
          var enAttente = false;
          function planifier() {
            if (enAttente) { return; }
            enAttente = true;
            setTimeout(function () { enAttente = false; nettoyer(); brancherVideo(); }, 250);
          }

          try {
            if (window.MutationObserver && document.body) {
              new MutationObserver(planifier).observe(document.body, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['style', 'class']
              });
            }
          } catch (e) {}

          /* Certains lecteurs repondent 200 avec une simple page « video
             indisponible » : aucune balise video n'apparait jamais, donc ni le
             WebView ni l'element video n'emettent d'erreur. Sans ce delai de garde,
             l'utilisateur resterait devant un ecran noir sans bascule automatique. */
          var essais = 0;
          var minuterie = setInterval(function () {
            essais++;
            nettoyer();
            if (brancherVideo()) {
              clearInterval(minuterie);
            } else if (essais > ESSAIS_MAX) {
              /* Meme sursis : la balise video n'existe parfois qu'apres le premier
                 geste, et la reclamer plus longtemps ne la ferait pas apparaitre. */
              if (demanderAppui()) {
                essais = 0;
                return;
              }

              clearInterval(minuterie);

              /* Aucune video atteignable. Reste a savoir si elle n'existe pas, ou si
                 elle est dans un cadre d'une autre origine — auquel cas elle joue
                 peut-etre tres bien, et declarer le lecteur mort couperait une lecture
                 en cours pour en essayer une autre. */
              var externe = cadreExterne();
              if (externe) {
                prevenir('onLecteurEnCadreExterne', externe);
              } else {
                prevenir('onAucuneVideo');
              }
            }
          }, 800);

          nettoyer();
          brancherVideo();

          /* --- 8. Commandes appelees depuis l'application --------------------- */
          window.allAnimeLire = function () {
            var video = trouverVideo();
            pauseDemandee = false;
            if (video) { try { video.play(); } catch (e) {} }
          };
          window.allAnimePause = function () {
            var video = trouverVideo();
            /* Une pause voulue par l'utilisateur ne doit pas etre prise pour un
               lecteur qui refuse de demarrer. */
            pauseDemandee = true;
            if (video) { try { video.pause(); } catch (e) {} }
          };
          window.allAnimeBasculer = function () {
            var video = trouverVideo();
            if (!video) { return; }
            try {
              if (video.paused) { pauseDemandee = false; video.play(); }
              else { pauseDemandee = true; video.pause(); }
            } catch (e) {}
          };
          window.allAnimeAvancer = function (secondes) {
            var video = trouverVideo();
            if (video && !isNaN(video.currentTime)) {
              try { video.currentTime = video.currentTime + secondes; } catch (e) {}
            }
          };
          window.allAnimeAllerA = function (secondes) {
            var video = trouverVideo();
            if (!video) { return; }
            try { video.currentTime = secondes; } catch (e) {}
          };
        })();
    """

    /** Le CSS transforme en litteral JavaScript sur une seule ligne. */
    private fun cssLitteral(): String {
        val compact = CSS_ANTI_OVERLAY
            .trimIndent()
            .replace("\r", "")
            .replace("\n", " ")
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        return "'$compact'"
    }
}
