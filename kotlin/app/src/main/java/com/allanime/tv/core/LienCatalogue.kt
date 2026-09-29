package com.allanime.tv.core

/**
 * Lecture des liens rencontres dans les pages du site.
 *
 * Le catalogue et le planning pointent vers la meme ressource sous deux formes :
 * `https://autre-domaine.tld/catalogue/liar-game/` dans la liste, et
 * `/catalogue/liar-game/saison1/vostfr/` dans le planning. Le nom d'hote n'est pas
 * toujours celui configure dans [AppConfig] — le site change de domaine sans
 * prevenir — et seul le slug nous interesse : c'est lui qui permet de reconstruire
 * l'URL de la fiche avec notre propre racine.
 */
object LienCatalogue {

    /**
     * Slug de la fiche designee par [lien], ou `null` si le lien n'en contient pas.
     *
     * Le repere est [segment], c'est-a-dire `catalogue` : le slug est celui qui le
     * suit. A defaut de repere — lien deja relatif a la fiche — on retient le premier
     * segment, ce qui couvre `liar-game/saison1/vostfr`.
     *
     * @param segment nom du segment repere ; injectable pour les tests, qui ne
     *   doivent pas dependre de la valeur du secret `BASE_URL_2A`.
     */
    fun slug(lien: String?, segment: String = AppConfig.segmentCatalogue): String? {
        val chemin = lien
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?: return null

        val segments = chemin.split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.endsWith(':') }

        val repere = segments.indexOfLast { it.equals(segment, true) }
        val slug = if (repere >= 0) segments.getOrNull(repere + 1) else segments.firstOrNull()

        // Un lien absolu sans segment de catalogue commencerait par le nom d'hote :
        // on refuse alors de deviner plutot que de fabriquer un slug fantaisiste.
        if (repere < 0 && chemin.contains("//")) return null

        return slug?.takeIf { it.isNotBlank() }
    }
}
