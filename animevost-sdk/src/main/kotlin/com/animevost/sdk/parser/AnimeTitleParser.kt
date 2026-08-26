package com.animevost.sdk.parser

internal data class AnimeTitleParts(
    val title: String,
    val originalTitle: String?,
    val episodeInfo: String?,
)

internal object AnimeTitleParser {
    fun split(rawTitle: String): AnimeTitleParts {
        val bracketValues = titleBracketRegex.findAll(rawTitle)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        val cleanTitle = rawTitle.replace(titleBracketRegex, "").trim()
        val parts = cleanTitle.split(" / ", limit = 2)

        return AnimeTitleParts(
            title = parts.firstOrNull()?.trim().orEmpty(),
            originalTitle = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
            episodeInfo = bracketValues.lastOrNull(),
        )
    }

    private val titleBracketRegex = Regex("""\s*\[([^\]]+)]""")
}
