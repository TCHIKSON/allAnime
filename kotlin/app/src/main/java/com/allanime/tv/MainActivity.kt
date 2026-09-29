package com.allanime.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.allanime.tv.ui.navigation.AppNavHost
import com.allanime.tv.ui.theme.AllAnimeTheme
import com.allanime.tv.ui.theme.InkBackground

/**
 * Activite unique de l'application (sections 14 et 38 du cahier des charges).
 *
 * Tous les ecrans, lecteur compris, vivent ici : aucune fenetre ni activite
 * supplementaire n'est ouverte pour lire une video.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // L'ecran reste allume pendant la lecture.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AllAnimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(InkBackground)
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
