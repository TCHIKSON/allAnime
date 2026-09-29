package com.allanime.tv.core

/**
 * Nettoyage des descriptions AniList pour un affichage TV.
 *
 * Meme avec `description(asHtml: false)`, AniList renvoie des sauts de ligne HTML,
 * quelques balises simples, des liens Markdown et des marqueurs de spoiler `~!...!~`.
 * On les reduit a du texte brut lisible a distance, sans jamais faire planter
 * l'affichage si le contenu est absent.
 */
object HtmlText {

    private val LINE_BREAK = Regex("(?i)<br\\s*/?>")
    private val HTML_TAG = Regex("<[^>]+>")
    private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val SPOILER = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL)
    private val BOLD_OR_ITALIC = Regex("__|\\*\\*")
    private val EXCESS_BLANK_LINES = Regex("\n{3,}")

    fun toPlainText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val plain = raw
            .let { SPOILER.replace(it, "") }
            .let { LINE_BREAK.replace(it, "\n") }
            .let { HTML_TAG.replace(it, "") }
            .let { MARKDOWN_LINK.replace(it, "$1") }
            .let { BOLD_OR_ITALIC.replace(it, "") }
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .let { EXCESS_BLANK_LINES.replace(it, "\n\n") }
            .trim()

        return plain.ifBlank { null }
    }
}
