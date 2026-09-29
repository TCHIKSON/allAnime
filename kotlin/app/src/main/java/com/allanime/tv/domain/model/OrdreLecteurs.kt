package com.allanime.tv.domain.model

/**
 * Ordre dans lequel les lecteurs d'un episode sont tentes.
 *
 * Le catalogue liste les lecteurs dans un ordre arbitraire, et certains hebergeurs
 * sont durablement hors service : commencer systematiquement par le premier fait
 * attendre l'utilisateur pour rien. L'ordre de tentative suit donc le comportement
 * reellement observe sur ce televiseur, le rang du catalogue ne servant plus qu'a
 * departager les hebergeurs jamais rencontres.
 *
 * L'ordre d'affichage, lui, reste celui du catalogue : « Lecteur 2 » designe bien le
 * deuxieme lecteur de la page, quel que soit l'ordre des tentatives.
 */
object OrdreLecteurs {

    /**
     * Indices de [lecteurs] du plus prometteur au moins prometteur.
     *
     * @param scores score par hebergeur, tel que tenu par
     *   [com.allanime.tv.data.lecteur.ClassementLecteurs]. Un hebergeur absent vaut 0,
     *   ce qui le place entre ceux qui ont echoue et ceux qui ont fonctionne.
     */
    fun ordonner(lecteurs: List<Lecteur>, scores: Map<String, Int>): List<Int> =
        lecteurs.indices.sortedWith(
            compareByDescending<Int> { index -> scores[lecteurs[index].hostLabel] ?: 0 }
                .thenBy { index -> index }
        )
}
