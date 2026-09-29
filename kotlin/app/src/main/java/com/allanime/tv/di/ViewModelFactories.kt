package com.allanime.tv.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Fabrique de ViewModel pour les ecrans qui recoivent des arguments de navigation
 * (identifiant d'anime, saison, episode...).
 */
inline fun <VM : ViewModel> viewModelFactoryFor(
    crossinline creation: () -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creation() as T
}
