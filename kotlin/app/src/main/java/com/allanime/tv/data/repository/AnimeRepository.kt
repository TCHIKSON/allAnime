package com.allanime.tv.data.repository

import com.allanime.tv.core.Slugifier
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.data.anilist.AniListService
import com.allanime.tv.domain.model.Anime
import java.util.concurrent.ConcurrentHashMap

/**
 * Acces aux fiches AniList.
 *
 * AniList n'alimente plus l'accueil — c'est le catalogue du site qui s'en charge — et
 * n'est donc plus sollicite qu'a partir d'une recherche : celle que lance l'ecran de
 * recherche, et celle que lance l'ouverture d'une serie pour enrichir sa fiche.
 *
 * Les fiches deja vues sont conservees en memoire : passer de l'accueil a la fiche
 * detaillee ne redeclenche donc aucun appel reseau (section 28). Si le cache est vide
 * — processus recycle par Android TV pendant que l'utilisateur etait ailleurs — la
 * fiche est rechargee par identifiant.
 */
class AnimeRepository(
    private val service: AniListService
) {

    private val parId = ConcurrentHashMap<Int, Anime>()

    /** Titre de catalogue slugifie vers identifiant AniList, pour ne chercher qu'une fois. */
    private val parTitre = ConcurrentHashMap<String, Int>()

    /**
     * Recherche AniList ; les resultats alimentent aussi le cache des fiches.
     *
     * Les animes Hentai sont retires de la reponse : ils n'apparaissent ni dans
     * l'ecran de recherche, ni comme fiche retrouvee a partir d'un titre du catalogue.
     */
    suspend fun rechercher(requete: String): List<Anime> {
        val resultat = service.search(requete.trim()).filterNot { it.estHentai }
        resultat.forEach { parId[it.id] = it }
        return resultat
    }

    /**
     * Fiche complete, depuis le cache si possible, sinon rechargee depuis AniList.
     *
     * Une fiche Hentai est rendue comme introuvable : sans cela, une recommandation
     * ou un lien direct ouvrirait ce que la recherche vient d'ecarter.
     */
    suspend fun fiche(id: Int): Anime? {
        parId[id]?.let { return it }
        return service.byId(id)?.takeUnless { it.estHentai }?.also { parId[id] = it }
    }

    /**
     * Depose une fiche dans le cache memoire.
     *
     * Sert aux fiches construites depuis la seule carte du catalogue, qu'AniList ne
     * connait pas : sans cela, le lecteur — qui recharge l'anime par identifiant —
     * ne les retrouverait pas.
     */
    fun memoriser(anime: Anime) {
        parId[anime.id] = anime
    }

    /**
     * Retrouve sur AniList la fiche correspondant a un titre du catalogue.
     *
     * C'est le raccord entre les deux sources : le catalogue donne un titre et un
     * slug, AniList donne le synopsis, les personnages, le staff et les notes. On
     * emprunte la meme recherche que l'ecran de recherche, ce qui evite d'ajouter une
     * requete GraphQL de plus.
     *
     * Le premier titre du catalogue est parfois francise (« Le Voyage de Chihiro »),
     * la ou AniList indexe surtout le romaji : les titres alternatifs de la carte
     * servent donc de repli, essayes dans l'ordre. La fiche retenue est celle dont un
     * titre correspond exactement, une fois les deux slugifies ; a defaut, le premier
     * resultat rendu par AniList, dont le classement par pertinence vaut mieux que
     * pas de fiche du tout.
     *
     * @return `null` seulement si aucune recherche n'aboutit.
     */
    suspend fun parTitres(titres: List<String>): Anime? {
        val essais = titres.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (essais.isEmpty()) return null

        essais.forEach { titre ->
            parTitre[Slugifier.slugify(titre)]?.let { id -> parId[id]?.let { return it } }
        }

        var repli: Anime? = null

        for (titre in essais) {
            val resultats = runCatchingCancellable { rechercher(titre) }.getOrNull().orEmpty()
            if (resultats.isEmpty()) continue

            val attendu = Slugifier.slugify(titre)
            val exact = resultats.firstOrNull { anime ->
                anime.candidatsCatalogue.any { Slugifier.slugify(it) == attendu }
            }

            if (exact != null) {
                essais.forEach { parTitre[Slugifier.slugify(it)] = exact.id }
                return exact
            }

            if (repli == null) repli = resultats.first()
        }

        repli?.let { trouve -> essais.forEach { parTitre[Slugifier.slugify(it)] = trouve.id } }
        return repli
    }
}
