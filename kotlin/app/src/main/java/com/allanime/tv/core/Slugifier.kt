package com.allanime.tv.core

import java.text.Normalizer
import java.util.Locale

/**
 * Slugification deterministe des titres d'animes (section 32 du cahier des charges).
 *
 * `Classroom of the Elite` devient `classroom-of-the-elite`.
 *
 * Regles appliquees, dans l'ordre :
 *  1. suppression des accents (decomposition NFD puis retrait des diacritiques) ;
 *  2. traitement de l'apostrophe, seul point ou le catalogue hesite — voir plus bas ;
 *  3. passage en minuscules ;
 *  4. remplacement de toute suite de caracteres non alphanumeriques par un tiret
 *     unique, ce qui absorbe espaces multiples, ponctuation et symboles ;
 *  5. retrait des tirets de debut et de fin.
 *
 * **L'apostrophe a deux traitements possibles**, parce que le catalogue emploie les
 * deux : `Grimm's Tales` peut y figurer sous `grimms-tales` comme sous
 * `grimm-s-tales`. [slugify] applique le premier — l'apostrophe disparait — et
 * [slugifySeparateur] le second — elle vaut separateur, comme n'importe quel autre
 * signe de ponctuation. [variantes] donne les deux, sans doublon : c'est ce que le
 * resolveur essaie a tour de role pour retrouver les saisons d'une serie.
 */
object Slugifier {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val APOSTROPHES = Regex("['‘’ʼ\"“”]")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val EDGE_HYPHENS = Regex("^-+|-+$")

    /** Forme historique : l'apostrophe est retiree (`Grimm's Tales` → `grimms-tales`). */
    fun slugify(raw: String?): String = construire(raw, apostropheSeparatrice = false)

    /**
     * Forme ou l'apostrophe est un separateur comme un autre
     * (`Grimm's Tales` → `grimm-s-tales`).
     */
    fun slugifySeparateur(raw: String?): String = construire(raw, apostropheSeparatrice = true)

    /**
     * Les deux formes, sans doublon ni valeur vide, dans l'ordre ou il faut les
     * essayer. Un titre sans apostrophe n'en produit qu'une seule.
     */
    fun variantes(raw: String?): List<String> =
        listOf(slugify(raw), slugifySeparateur(raw))
            .filter { it.isNotEmpty() }
            .distinct()

    private fun construire(raw: String?, apostropheSeparatrice: Boolean): String {
        if (raw.isNullOrBlank()) return ""

        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS.replace(decomposed, "")
        // Remplacer l'apostrophe par une espace suffit : l'etape 4 la transformera en
        // tiret, exactement comme les autres signes de ponctuation.
        val apostrophes = APOSTROPHES.replace(withoutDiacritics, if (apostropheSeparatrice) " " else "")
        val lowercase = apostrophes.lowercase(Locale.ROOT)
        val hyphenated = NON_ALPHANUMERIC.replace(lowercase, "-")
        return EDGE_HYPHENS.replace(hyphenated, "")
    }
}
