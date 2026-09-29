package com.allanime.tv.data.anilist

import com.allanime.tv.data.net.HttpClients
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Verifie que les requetes GraphQL sont acceptees par AniList et que la reponse se
 * deserialise vers les modeles de domaine.
 *
 * Depend du reseau : ignore par defaut, a lancer manuellement apres une evolution
 * des requetes.
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*AniListIntegrationTest"
 */
@Ignore("Depend du reseau et de l'API AniList ; a lancer manuellement.")
class AniListIntegrationTest {

    private val service = AniListService(HttpClients.shared)

    @Test
    fun `la recherche par titre renvoie la bonne fiche`() = runBlocking {
        val resultats = service.search("Classroom of the Elite")

        assertTrue(resultats.isNotEmpty())
        println("Recherche : " + resultats.map { it.displayTitle })
        assertTrue(
            resultats.any { it.catalogueTitle?.contains("Classroom", ignoreCase = true) == true }
        )

        // C'est cette requete qui enrichit la fiche ouverte depuis le catalogue :
        // les champs riches doivent bien arriver jusqu'au domaine.
        assertTrue(resultats.any { it.genres.isNotEmpty() })
        assertTrue(resultats.any { it.characters.isNotEmpty() })
        assertTrue(resultats.any { it.studios.isNotEmpty() })
    }

    @Test
    fun `une fiche est rechargeable par identifiant`() = runBlocking {
        val anime = service.byId(98659) // Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e

        assertNotNull(anime)
        println("Fiche : ${anime?.displayTitle} / titre catalogue : ${anime?.catalogueTitle}")
        assertEquals(98659, anime?.id)
    }
}
