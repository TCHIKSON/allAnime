package com.allanime.tv

import android.app.Application
import com.allanime.tv.di.ServiceLocator

/** Point d'entree du processus : initialise l'assemblage des dependances. */
class AllAnimeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialiser(this)
    }
}
