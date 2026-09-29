package com.allanime.tv.core

import androidx.annotation.StringRes
import com.allanime.tv.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlinx.serialization.SerializationException

/**
 * Erreurs applicatives presentables a l'utilisateur (section 27 du cahier des charges).
 *
 * Chaque cas porte un message localise ; la cause technique est conservee pour les
 * logs mais n'est jamais affichee telle quelle sur la TV.
 */
sealed class AppError(@StringRes val messageRes: Int, open val cause: Throwable? = null) {

    data class Network(override val cause: Throwable? = null) :
        AppError(R.string.error_network, cause)

    data class Timeout(override val cause: Throwable? = null) :
        AppError(R.string.error_timeout, cause)

    data class AniList(override val cause: Throwable? = null) :
        AppError(R.string.error_anilist, cause)

    data class InvalidData(override val cause: Throwable? = null) :
        AppError(R.string.error_invalid_data, cause)

    data class NotFound(override val cause: Throwable? = null) :
        AppError(R.string.error_not_found, cause)

    data class CatalogueUnavailable(override val cause: Throwable? = null) :
        AppError(R.string.error_catalogue_unavailable, cause)

    data class EpisodesUnavailable(override val cause: Throwable? = null) :
        AppError(R.string.error_episodes_unavailable, cause)

    data class PlayerUnreachable(override val cause: Throwable? = null) :
        AppError(R.string.error_player_unreachable, cause)

    data class Unknown(override val cause: Throwable? = null) :
        AppError(R.string.error_unknown, cause)
}

/**
 * Exceptions levees par les couches de donnees, converties en [AppError] par [toAppError].
 */
sealed class DataException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** AniList a repondu, mais avec un statut HTTP ou un corps `errors` inexploitable. */
    class AniListFailure(message: String, cause: Throwable? = null) : DataException(message, cause)

    /** Le corps recu n'a pas la forme attendue (JSON ou HTML). */
    class MalformedPayload(message: String, cause: Throwable? = null) :
        DataException(message, cause)

    /** La fiche catalogue existe mais ne contient aucun panneau de saison exploitable. */
    class CatalogueUnavailable(message: String, cause: Throwable? = null) :
        DataException(message, cause)

    /** Le fichier `episodes.js` de la saison est absent ou vide. */
    class EpisodesUnavailable(message: String, cause: Throwable? = null) :
        DataException(message, cause)

    /** Ressource demandee introuvable (anime, saison ou episode). */
    class NotFound(message: String, cause: Throwable? = null) : DataException(message, cause)
}

/** Traduit une exception technique en erreur presentable. */
fun Throwable.toAppError(): AppError = when (this) {
    is DataException.AniListFailure -> AppError.AniList(this)
    is DataException.MalformedPayload -> AppError.InvalidData(this)
    is DataException.CatalogueUnavailable -> AppError.CatalogueUnavailable(this)
    is DataException.EpisodesUnavailable -> AppError.EpisodesUnavailable(this)
    is DataException.NotFound -> AppError.NotFound(this)
    is SerializationException -> AppError.InvalidData(this)
    is SocketTimeoutException, is TimeoutException -> AppError.Timeout(this)
    is UnknownHostException -> AppError.Network(this)
    is IOException -> AppError.Network(this)
    else -> AppError.Unknown(this)
}
