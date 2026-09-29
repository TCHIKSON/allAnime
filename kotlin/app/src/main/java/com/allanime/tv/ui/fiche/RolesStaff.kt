package com.allanime.tv.ui.fiche

/**
 * Traduction des fonctions du staff, qu'AniList renvoie en anglais et en texte
 * libre.
 *
 * Les intitules sont souvent suffixes par une precision — `Director (eps 1-3)`,
 * `Producer (English)` — que l'on conserve telle quelle. Seule la fonction connue en
 * tete de chaine est traduite ; un intitule inconnu est affiche sans modification
 * plutot que deforme.
 */
internal object RolesStaff {

    private val TRADUCTIONS = mapOf(
        "Chief Animation Director" to "Direction générale de l'animation",
        "Original Character Design" to "Design original des personnages",
        "Theme Song Performance" to "Interprétation du générique",
        "Theme Song Composition" to "Composition du générique",
        "Theme Song Arrangement" to "Arrangement du générique",
        "Theme Song Lyrics" to "Paroles du générique",
        "Sub Character Design" to "Design des personnages secondaires",
        "Photography Director" to "Direction de la photographie",
        "Assistant Animation Director" to "Assistant à la direction de l'animation",
        "Assistant Episode Director" to "Assistant à la réalisation d'épisode",
        "Animation Director" to "Direction de l'animation",
        "In-Between Animation" to "Animation intermédiaire",
        "Executive Producer" to "Production exécutive",
        "Episode Director" to "Réalisation d'épisode",
        "Series Composition" to "Composition de la série",
        "Assistant Director" to "Assistant réalisateur",
        "2nd Key Animation" to "Animation clé (seconde équipe)",
        "Original Creator" to "Œuvre originale",
        "Character Design" to "Design des personnages",
        "System Engineer" to "Ingénierie système",
        "Background Art" to "Décors",
        "Sound Director" to "Direction du son",
        "Art Director" to "Direction artistique",
        "Key Animation" to "Animation clé",
        "Color Design" to "Conception des couleurs",
        "Design Works" to "Recherches graphiques",
        "Original Story" to "Histoire originale",
        "Chief Director" to "Réalisation en chef",
        "Director" to "Réalisation",
        "Producer" to "Production",
        "Storyboard" to "Story-board",
        "Script" to "Scénario",
        "Planning" to "Planification",
        "Editing" to "Montage",
        "Music" to "Musique",
        "Setting" to "Décors et accessoires"
    )

    /** Les clefs les plus longues d'abord, pour que `Chief Director` gagne sur `Director`. */
    private val CLEFS_PAR_LONGUEUR = TRADUCTIONS.keys.sortedByDescending { it.length }

    fun traduire(role: String?): String? {
        val valeur = role?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val clef = CLEFS_PAR_LONGUEUR.firstOrNull { valeur.startsWith(it, ignoreCase = true) }
            ?: return valeur

        val suffixe = valeur.substring(clef.length).trim()
        val traduction = TRADUCTIONS.getValue(clef)

        return if (suffixe.isEmpty()) traduction else "$traduction $suffixe"
    }
}
