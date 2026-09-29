package com.allanime.tv.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.allanime.tv.core.AppConfig
import com.allanime.tv.domain.model.InventaireQualites
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json

/**
 * Lecteur web embarque dans la fenetre principale (sections 14 a 22 du cahier des
 * charges). Aucune activite ni fenetre supplementaire n'est ouverte : le WebView vit
 * dans [conteneur], place directement dans l'ecran Compose du lecteur.
 *
 * Responsabilites :
 *  - configuration du WebView (autoplay, cookies, User-Agent, fenetres multiples) ;
 *  - filtrage reseau via [AdBlockRules] dans `shouldInterceptRequest` ;
 *  - controle des redirections dans `shouldOverrideUrlLoading` ;
 *  - injection du CSS/JS de [OverlayScripts] apres chaque chargement ;
 *  - relais du plein ecran HTML5 vers l'interface Compose.
 */
class WebPlayerHost(
    context: Context,
    private val ecouteur: Ecouteur
) {

    /** Evenements remontes a l'interface. */
    interface Ecouteur {
        fun surChargement(enCours: Boolean)
        fun surErreur(description: String?)
        fun surPopupBloque()
        fun surRedirectionBloquee(url: String)
        fun surOverlaysMasques(nombre: Int)
        fun surVideoDetectee()
        fun surAutoplayRefuse()
        fun surLectureTerminee()
        fun surPleinEcranHtml5(actif: Boolean)

        /**
         * Des images defilent reellement : le lecteur courant est utilisable.
         * C'est ce signal, et non le simple chargement de la page, qui fait foi.
         */
        fun surLectureDemarree()

        /**
         * Aucune image apres le delai de garde : source injouable ou trop lente.
         * L'interface enchaine sur le lecteur suivant sans rien demander.
         */
        fun surDemarrageImpossible()

        /** Qualites proposees par le lecteur courant, telles qu'il les expose. */
        fun surQualites(inventaire: InventaireQualites)

        /** Position courante de la video ; [dureeSecondes] vaut 0 si elle est inconnue. */
        fun surProgression(positionSecondes: Int, dureeSecondes: Int)

        /** Etat reel de la video, pour tenir le bouton Lire / Pause a jour. */
        fun surEtatLecture(enLecture: Boolean)
    }

    /** Vue a inserer dans l'arbre Compose. */
    val conteneur: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
    }

    private val webView: WebView = WebView(context)

    /** Hote du lecteur en cours : sert a distinguer navigation legitime et redirection. */
    private var hoteCourant: String? = null

    private var vuePleinEcran: View? = null
    private var rappelPleinEcran: WebChromeClient.CustomViewCallback? = null

    init {
        configurer()
        conteneur.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurer() {
        webView.setBackgroundColor(Color.BLACK)

        // Le WebView ne prend jamais le focus : sinon la page capte les touches
        // directionnelles de la telecommande et les commandes de l'application
        // (pause, saut, changement de lecteur) deviennent inatteignables. La lecture
        // est pilotee par JavaScript, le focus web n'a donc aucune utilite ici.
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        conteneur.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        conteneur.isFocusable = false

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true

            // Autoplay : indispensable pour demarrer sans interaction (section 18).
            mediaPlaybackRequiresUserGesture = false

            // User-Agent centralise dans la configuration (section 19).
            userAgentString = AppConfig.playerUserAgent

            loadWithOverviewMode = true
            useWideViewPort = true

            // Fenetres multiples refusees : window.open et target=_blank restent
            // sans effet (sections 16 et 21).
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false

            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }

        // Cookies conserves d'un episode a l'autre (section 17).
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(Pont(), OverlayScripts.NOM_PONT)
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
    }

    /**
     * Charge un lecteur. L'hote devient la reference des navigations autorisees.
     *
     * Une adresse YouTube ne peut pas etre chargee telle quelle : faute de page
     * parente, YouTube refuse de jouer la video et propose de l'ouvrir sur son site.
     * Elle passe donc par la page locale de [YouTubeEmbed], servie sous l'origine de
     * YouTube, qui porte l'iframe et expose les memes commandes que les autres
     * lecteurs.
     */
    fun charger(url: String) {
        val video = YouTubeEmbed.identifiant(url)
        if (video != null) {
            hoteCourant = YouTubeEmbed.HOTE_ORIGINE
            ecouteur.surChargement(true)
            webView.loadDataWithBaseURL(
                YouTubeEmbed.ORIGINE,
                YouTubeEmbed.page(video),
                "text/html",
                "utf-8",
                null
            )
            return
        }

        hoteCourant = runCatching { Uri.parse(url).host }.getOrNull()
        ecouteur.surChargement(true)
        webView.loadUrl(url)
    }

    fun lire() = executer("window.allAnimeLire && window.allAnimeLire();")

    fun pause() = executer("window.allAnimePause && window.allAnimePause();")

    fun basculerLecture() = executer("window.allAnimeBasculer && window.allAnimeBasculer();")

    fun avancer(secondes: Int) =
        executer("window.allAnimeAvancer && window.allAnimeAvancer($secondes);")

    /** Positionne la lecture a un instant precis, pour reprendre ou ela ou on s'etait arrete. */
    fun allerA(secondes: Int) =
        executer("window.allAnimeAllerA && window.allAnimeAllerA($secondes);")

    /** Applique une qualite de l'inventaire remonte par [Ecouteur.surQualites]. */
    fun choisirQualite(id: String) {
        val echappe = id.replace("\\", "").replace("'", "")
        executer("window.allAnimeChoisirQualite && window.allAnimeChoisirQualite('$echappe');")
    }

    /** `true` si un plein ecran HTML5 etait actif et vient d'etre referme. */
    fun quitterPleinEcranHtml5(): Boolean {
        val rappel = rappelPleinEcran ?: return false
        rappel.onCustomViewHidden()
        retirerVuePleinEcran()
        return true
    }

    fun surPause() {
        webView.onPause()
        webView.pauseTimers()
        CookieManager.getInstance().flush()
    }

    fun surReprise() {
        webView.onResume()
        webView.resumeTimers()
    }

    /** Libere le WebView ; a appeler quand l'ecran du lecteur disparait. */
    fun detruire() {
        runCatching {
            quitterPleinEcranHtml5()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface(OverlayScripts.NOM_PONT)
            conteneur.removeAllViews()
            webView.destroy()
        }.onFailure { Log.w(TAG, "Liberation du lecteur incomplete", it) }
        CookieManager.getInstance().flush()
    }

    private fun executer(script: String) {
        runCatching { webView.evaluateJavascript(script, null) }
            .onFailure { Log.w(TAG, "Commande lecteur ignoree", it) }
    }

    private fun retirerVuePleinEcran() {
        vuePleinEcran?.let(conteneur::removeView)
        vuePleinEcran = null
        rappelPleinEcran = null
        webView.visibility = View.VISIBLE
        ecouteur.surPleinEcranHtml5(false)
    }

    /**
     * Politique de navigation (section 21).
     *
     * Par defaut la page reste dans l'application : seules les navigations vers le
     * meme domaine, les sous-cadres et les redirections serveur du lecteur sont
     * suivies. Tout le reste est refuse et signale a l'interface.
     */
    private fun autoriserNavigation(url: String, pourCadrePrincipal: Boolean, estRedirection: Boolean): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false

        // Schemas applicatifs (intent://, market://...) : jamais suivis.
        if (uri.scheme !in SCHEMAS_AUTORISES) return false

        if (AdBlockRules.estBloquee(url)) return false
        if (!pourCadrePrincipal) return true
        if (AdBlockRules.memeDomaine(uri.host, hoteCourant)) return true

        // Redirection emise par le serveur du lecteur : souvent un CDN, on la suit.
        return estRedirection
    }

    private fun reponseVide(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )

    private inner class Client : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String) {
            hoteCourant = runCatching { Uri.parse(url).host }.getOrNull() ?: hoteCourant
            view.evaluateJavascript(OverlayScripts.JS_PROTECTION, null)
            ecouteur.surChargement(false)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            val autorisee = autoriserNavigation(url, request.isForMainFrame, request.isRedirect)
            if (!autorisee) ecouteur.surRedirectionBloquee(url)
            return !autorisee
        }

        @Deprecated("Conserve pour les API < 24 ; la variante WebResourceRequest prend le relais ensuite.")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val autorisee = autoriserNavigation(url, pourCadrePrincipal = true, estRedirection = false)
            if (!autorisee) ecouteur.surRedirectionBloquee(url)
            return !autorisee
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? =
            if (AdBlockRules.estBloquee(request.url.toString())) reponseVide() else null

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                error.description?.toString()
            } else {
                null
            }
            ecouteur.surChargement(false)
            ecouteur.surErreur(description)
        }
    }

    private inner class Chrome : WebChromeClient() {

        /** Aucune fenetre supplementaire : la page reste dans l'activite (section 16). */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            ecouteur.surPopupBloque()
            return false
        }

        /** Plein ecran HTML5 demande par le lecteur (section 25). */
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (vuePleinEcran != null) {
                callback.onCustomViewHidden()
                return
            }
            vuePleinEcran = view
            rappelPleinEcran = callback
            webView.visibility = View.GONE
            conteneur.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            ecouteur.surPleinEcranHtml5(true)
        }

        override fun onHideCustomView() {
            if (vuePleinEcran == null) return
            retirerVuePleinEcran()
        }

        override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
            request.deny()
        }
    }

    /** Pont JavaScript : seules les methodes annotees sont visibles depuis la page. */
    private inner class Pont {

        @JavascriptInterface
        fun onPopupBloque() = surFilDInterface { ecouteur.surPopupBloque() }

        @JavascriptInterface
        fun onOverlaysMasques(nombre: Int) = surFilDInterface {
            ecouteur.surOverlaysMasques(nombre)
        }

        @JavascriptInterface
        fun onVideoDetectee(trouvee: Boolean) = surFilDInterface {
            if (trouvee) ecouteur.surVideoDetectee()
        }

        @JavascriptInterface
        fun onAutoplayRefuse() = surFilDInterface { ecouteur.surAutoplayRefuse() }

        @JavascriptInterface
        fun onLectureTerminee() = surFilDInterface { ecouteur.surLectureTerminee() }

        @JavascriptInterface
        fun onErreurLecture() = surFilDInterface { ecouteur.surErreur(null) }

        /** Page chargee, mais aucune video n'est apparue : lecteur considere mort. */
        @JavascriptInterface
        fun onAucuneVideo() = surFilDInterface { ecouteur.surErreur("aucune video") }

        @JavascriptInterface
        fun onProgression(positionSecondes: Int, dureeSecondes: Int) = surFilDInterface {
            ecouteur.surProgression(positionSecondes, dureeSecondes)
        }

        @JavascriptInterface
        fun onEtatLecture(enLecture: Boolean) = surFilDInterface {
            ecouteur.surEtatLecture(enLecture)
        }

        @JavascriptInterface
        fun onLectureDemarree() = surFilDInterface { ecouteur.surLectureDemarree() }

        @JavascriptInterface
        fun onDemarrageImpossible() = surFilDInterface { ecouteur.surDemarrageImpossible() }

        /**
         * Inventaire des qualites, transmis en JSON parce que le pont JavaScript ne
         * sait passer que des types simples. Un inventaire illisible est ignore :
         * l'interface garde alors son entree automatique.
         */
        @JavascriptInterface
        fun onQualites(inventaire: String) {
            val decode = runCatching { jsonQualites.decodeFromString<InventaireQualites>(inventaire) }
                .onFailure { Log.w(TAG, "Inventaire de qualites illisible", it) }
                .getOrNull() ?: return

            surFilDInterface { ecouteur.surQualites(decode) }
        }

        /** Les rappels arrivent depuis un fil JavaScript : on repasse sur l'UI. */
        private fun surFilDInterface(action: () -> Unit) {
            webView.post(action)
        }
    }

    private companion object {
        const val TAG = "WebPlayerHost"
        val SCHEMAS_AUTORISES = setOf("http", "https")

        /** Les pages evoluent : une clef inconnue ne doit pas perdre l'inventaire. */
        val jsonQualites = Json { ignoreUnknownKeys = true }
    }
}
