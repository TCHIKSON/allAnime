package com.allanime.tv.core

/**
 * Etat d'un ecran ou d'un bloc de contenu.
 *
 * Les quatre cas correspondent aux etats demandes par la section 2 du cahier des
 * charges : chargement, succes, aucune donnee et erreur.
 */
sealed interface UiState<out T> {

    /** Chargement en cours ; [message] decrit l'operation (section 29). */
    data class Loading(val messageRes: Int) : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    /** La requete a abouti mais ne renvoie aucune donnee exploitable. */
    data class Empty(val messageRes: Int) : UiState<Nothing>

    data class Failure(val error: AppError) : UiState<Nothing>
}

/** Donnees courantes si l'etat est [UiState.Success], `null` sinon. */
val <T> UiState<T>.dataOrNull: T?
    get() = (this as? UiState.Success<T>)?.data
