package com.allanime.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quels episodes l'historique considere comme deja ouverts.
 *
 * C'est ce qui decide du flou des vignettes : une vignette nette raconte la scene
 * d'un episode, elle ne doit donc l'etre que pour ce qui a reellement ete regarde.
 */
class EpisodesOuvertsTest {

    private fun progression(
        indexSaison: Int = 0,
        numeroEpisode: Int = 1,
        ouverts: Map<Int, Set<Int>> = emptyMap()
    ) = ProgressionAnime(
        animeId = 1,
        titre = "Serie",
        episodesOuverts = ouverts,
        indexSaison = indexSaison,
        nomSaison = "Saison ${indexSaison + 1}",
        codeLangue = "VOSTFR",
        numeroEpisode = numeroEpisode
    )

    @Test
    fun `seul un episode reellement ouvert compte`() {
        // Sauter directement a l'episode 10 ne veut pas dire avoir vu les neuf
        // precedents : c'etait le defaut de la version basee sur le rang atteint.
        val vue = progression(numeroEpisode = 10, ouverts = mapOf(0 to setOf(10)))

        assertTrue(vue.aEteOuvert(0, 10))
        assertFalse(vue.aEteOuvert(0, 9))
        assertFalse(vue.aEteOuvert(0, 1))
    }

    @Test
    fun `les episodes s accumulent au fil des lectures`() {
        var ouverts = emptyMap<Int, Set<Int>>()
        val base = progression()

        listOf(3, 8, 4).forEach { numero ->
            ouverts = base.copy(episodesOuverts = ouverts).avecEpisodeOuvert(0, numero)
        }

        val vue = progression(numeroEpisode = 4, ouverts = ouverts)
        listOf(3, 4, 8).forEach { assertTrue("episode $it", vue.aEteOuvert(0, it)) }
        listOf(1, 2, 5, 7).forEach { assertFalse("episode $it", vue.aEteOuvert(0, it)) }
    }

    @Test
    fun `chaque saison a sa propre liste`() {
        val vue = progression(
            indexSaison = 1,
            numeroEpisode = 2,
            ouverts = mapOf(0 to setOf(5), 1 to setOf(2))
        )

        assertTrue(vue.aEteOuvert(0, 5))
        assertTrue(vue.aEteOuvert(1, 2))
        // Le meme numero dans une autre saison est un autre episode.
        assertFalse(vue.aEteOuvert(1, 5))
        assertFalse(vue.aEteOuvert(0, 2))
    }

    @Test
    fun `une entree ecrite avant la liste garde son episode`() {
        // Les historiques deja sur le televiseur n'ont pas le champ : ils ne
        // connaissent qu'un episode, mais ils le connaissent.
        val ancienne = progression(indexSaison = 2, numeroEpisode = 7)

        assertTrue(ancienne.aEteOuvert(2, 7))
        assertFalse(ancienne.aEteOuvert(2, 6))
        assertFalse(ancienne.aEteOuvert(1, 7))
    }
}
