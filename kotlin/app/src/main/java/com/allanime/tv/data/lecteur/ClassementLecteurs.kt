package com.allanime.tv.data.lecteur

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Score de fiabilite par hebergeur de lecteur, conserve sur le televiseur.
 *
 * Un hebergeur gagne un point quand des images ont reellement defile, et en perd un
 * quand il n'a rien affiche. Ce score sert uniquement a choisir l'ordre des
 * tentatives ([com.allanime.tv.domain.model.OrdreLecteurs]) : un hebergeur mort
 * cesse d'etre essaye en premier, sans jamais etre definitivement ecarte.
 *
 * Le score est borne dans les deux sens : un hebergeur tombe en panne pendant une
 * semaine doit pouvoir redevenir prioritaire en quelques succes, et non apres
 * cinquante. C'est la raison de [BORNE].
 */
class ClassementLecteurs(
    private val dataStore: DataStore<Preferences>
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serialiseur = MapSerializer(String.serializer(), Int.serializer())

    /** Scores connus ; un hebergeur absent vaut implicitement 0. */
    suspend fun scores(): Map<String, Int> = decoder(dataStore.data.first()[CLE_SCORES])

    /** Des images ont defile : cet hebergeur remonte dans l'ordre des tentatives. */
    suspend fun signalerSucces(hote: String) = ajuster(hote, +1)

    /** Rien ne s'est affiche : cet hebergeur passe derriere les autres. */
    suspend fun signalerEchec(hote: String) = ajuster(hote, -1)

    private suspend fun ajuster(hote: String, delta: Int) {
        val clef = hote.trim()
        if (clef.isEmpty()) return

        dataStore.edit { preferences ->
            val actuels = decoder(preferences[CLE_SCORES]).toMutableMap()
            val nouveau = ((actuels[clef] ?: 0) + delta).coerceIn(-BORNE, BORNE)
            actuels[clef] = nouveau
            preferences[CLE_SCORES] = json.encodeToString(serialiseur, actuels)
        }
    }

    /** Un stockage illisible ne doit jamais empecher la lecture : on repart a vide. */
    private fun decoder(brut: String?): Map<String, Int> {
        if (brut.isNullOrBlank()) return emptyMap()

        return runCatching { json.decodeFromString(serialiseur, brut) }
            .onFailure { Log.w(TAG, "Classement des lecteurs illisible, remis a zero", it) }
            .getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "ClassementLecteurs"

        /** Plafond et plancher du score, pour garder le classement reversible. */
        const val BORNE = 3

        val CLE_SCORES = stringPreferencesKey("scores_hebergeurs")
    }
}
