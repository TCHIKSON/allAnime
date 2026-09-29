package com.allanime.tv.data.anilist

import com.allanime.tv.core.AppConfig
import com.allanime.tv.core.DataException
import com.allanime.tv.data.net.await
import com.allanime.tv.domain.model.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Acces a l'API GraphQL AniList (section 4 du cahier des charges).
 *
 * Toutes les requetes sont des POST JSON vers [AppConfig.aniListEndpoint], avec un
 * corps contenant `query` et, le cas echeant, `variables`.
 */
class AniListService(
    private val client: OkHttpClient,
    private val endpoint: String = AppConfig.aniListEndpoint
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Recherche par titre ; renvoie une liste vide si AniList ne trouve rien. */
    suspend fun search(query: String, perPage: Int = SEARCH_PER_PAGE): List<Anime> {
        val body = GraphQlRequest(
            query = AniListQueries.search(perPage),
            variables = mapOf("search" to JsonPrimitive(query))
        )
        val raw = post(json.encodeToString(GraphQlRequest.serializer(), body))
        val response = decode(raw, PageResponse.serializer())
        response.errors.firstOrNull()?.let { error ->
            throw DataException.AniListFailure(error.message ?: "Reponse AniList en erreur")
        }
        return response.data?.page?.media.orEmpty().map(AniListMapper::toDomain)
    }

    /** Fiche unique ; renvoie `null` si AniList ne connait pas cet identifiant. */
    suspend fun byId(id: Int): Anime? {
        val body = GraphQlRequest(
            query = AniListQueries.byId(),
            variables = mapOf("id" to JsonPrimitive(id))
        )
        val raw = post(json.encodeToString(GraphQlRequest.serializer(), body))
        val response = decode(raw, MediaResponse.serializer())
        val media = response.data?.media
        if (media == null && response.errors.isNotEmpty()) {
            throw DataException.NotFound(
                response.errors.first().message ?: "Anime introuvable sur AniList"
            )
        }
        return media?.let(AniListMapper::toDomain)
    }

    private suspend fun post(body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", JSON_MEDIA_TYPE)
            .header("Accept", JSON_MEDIA_TYPE)
            .post(body.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        client.newCall(request).await().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DataException.AniListFailure(
                    "AniList a repondu ${response.code} ${response.message}"
                )
            }
            payload
        }
    }

    private fun <T> decode(raw: String, deserializer: kotlinx.serialization.KSerializer<T>): T =
        try {
            json.decodeFromString(deserializer, raw)
        } catch (error: SerializationException) {
            throw DataException.MalformedPayload("Reponse AniList illisible", error)
        }

    companion object {
        /** Valeur de la requete de reference du cahier des charges (section 4.3). */
        const val SEARCH_PER_PAGE = 5

        private const val JSON_MEDIA_TYPE = "application/json"
    }
}
