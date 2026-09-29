package com.allanime.tv.data.planning

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.allanime.tv.data.scraper.PlanningScraper
import com.allanime.tv.domain.model.Planning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Planning hebdomadaire, conserve sur le televiseur.
 *
 * Le site republie son planning « tous les lundis soir ». Deux consequences :
 *
 *  - la copie locale reste fidele toute la semaine, donc l'accueil affiche les
 *    sorties du jour des son ouverture, sans attendre le reseau ;
 *  - a chaque lancement, on verifie quand meme en arriere-plan. Le stockage n'est
 *    reecrit que si le site a publie autre chose : un planning identique ne fait donc
 *    pas clignoter l'accueil, puisque le flux DataStore n'emet rien.
 *
 * Meme stockage que l'historique : une seule clef contenant du JSON. Le volume est
 * d'une centaine de lignes, une base de donnees serait disproportionnee.
 */
class PlanningRepository(
    private val dataStore: DataStore<Preferences>,
    private val scraper: PlanningScraper
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val verrou = Mutex()

    /** Instant du dernier telechargement reussi, pour ne pas le refaire coup sur coup. */
    @Volatile
    private var derniereVerification = 0L

    /** Planning conserve localement, `null` tant qu'aucun n'a ete telecharge. */
    val planning: Flow<Planning?> = dataStore.data.map { preferences ->
        decoder(preferences[CLE_PLANNING])
    }

    /** Copie locale a l'instant present, sans attendre la prochaine emission. */
    suspend fun cache(): Planning? = planning.first()

    /**
     * Telecharge le planning et met la copie locale a jour si le site a change.
     *
     * L'accueil et l'ecran de planning appellent tous deux cette methode ; passer de
     * l'un a l'autre ne doit pas retelecharger la page. Les appels sont donc
     * serialises, et un telechargement reussi il y a moins de [FENETRE_MS] est
     * considere comme encore bon.
     */
    suspend fun synchroniser(): Planning? = verrou.withLock {
        val maintenant = System.currentTimeMillis()
        val recent = maintenant - derniereVerification < FENETRE_MS

        if (recent) return@withLock cache()

        val frais = scraper.fetch()
        val actuel = cache()

        if (actuel == null || !actuel.memeContenu(frais)) {
            enregistrer(frais)
        }

        derniereVerification = maintenant
        frais
    }

    private suspend fun enregistrer(planning: Planning) {
        dataStore.edit { preferences ->
            preferences[CLE_PLANNING] = json.encodeToString(Planning.serializer(), planning)
        }
    }

    /** Un stockage illisible ne doit jamais faire planter l'accueil : on repart a vide. */
    private fun decoder(brut: String?): Planning? {
        if (brut.isNullOrBlank()) return null

        return runCatching { json.decodeFromString(Planning.serializer(), brut) }
            .onFailure { Log.w(TAG, "Planning conserve illisible, ignore", it) }
            .getOrNull()
    }

    private companion object {
        const val TAG = "PlanningRepository"

        /** Deux ecrans ouverts coup sur coup partagent le meme telechargement. */
        const val FENETRE_MS = 60_000L
        val CLE_PLANNING = stringPreferencesKey("planning")
    }
}
