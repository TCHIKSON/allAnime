package com.allanime.tv.core

import kotlin.coroutines.cancellation.CancellationException

/**
 * Variante de `runCatching` sure en coroutine : l'annulation continue de se propager
 * au lieu d'etre transformee en echec silencieux.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
