package com.allanime.tv.data.historique

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.allanime.tv.domain.model.ProgressionAnime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Historique de visionnage conserve sur le televiseur (section « Reprendre votre
 * visionnage » de l'accueil).
 *
 * Stockage volontairement simple : une seule cle DataStore contenant la liste au
 * format JSON. Le volume est minuscule — au plus [MAX_ENTREES] entrees — et cela
 * evite d'embarquer une base de donnees pour quelques champs.
 */
class HistoriqueRepository(
    private val dataStore: DataStore<Preferences>
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serialiseur = ListSerializer(ProgressionAnime.serializer())

    /** Progressions, de la plus recente a la plus ancienne. */
    val progressions: Flow<List<ProgressionAnime>> = dataStore.data.map { preferences ->
        decoder(preferences[CLE_PROGRESSIONS])
    }

    /**
     * Enregistre ou met a jour l'avancement d'un anime.
     *
     * Une seule entree est conservee par anime : reprendre un autre episode remplace
     * la precedente, ce qui est bien le comportement attendu d'une liste « reprendre
     * le visionnage ».
     */
    suspend fun enregistrer(progression: ProgressionAnime) {
        dataStore.edit { preferences ->
            val actuelles = decoder(preferences[CLE_PROGRESSIONS])

            val mises = buildList {
                add(progression)
                addAll(actuelles.filterNot { it.animeId == progression.animeId })
            }
                .sortedByDescending { it.majEpochSeconde }
                .take(MAX_ENTREES)

            preferences[CLE_PROGRESSIONS] = json.encodeToString(serialiseur, mises)
        }
    }

    /** Progression connue pour cet anime, ou `null` s'il n'a jamais ete ouvert. */
    suspend fun progressionPour(animeId: Int): ProgressionAnime? =
        progressionsActuelles().firstOrNull { it.animeId == animeId }

    suspend fun supprimer(animeId: Int) {
        dataStore.edit { preferences ->
            val restantes = decoder(preferences[CLE_PROGRESSIONS])
                .filterNot { it.animeId == animeId }
            preferences[CLE_PROGRESSIONS] = json.encodeToString(serialiseur, restantes)
        }
    }

    private suspend fun progressionsActuelles(): List<ProgressionAnime> = progressions.first()

    /** Un stockage illisible ne doit jamais faire planter l'accueil : on repart a vide. */
    private fun decoder(brut: String?): List<ProgressionAnime> {
        if (brut.isNullOrBlank()) return emptyList()

        return runCatching { json.decodeFromString(serialiseur, brut) }
            .onFailure { Log.w(TAG, "Historique illisible, remis a zero", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "HistoriqueRepository"
        const val MAX_ENTREES = 20
        val CLE_PROGRESSIONS = stringPreferencesKey("progressions")
    }
}
