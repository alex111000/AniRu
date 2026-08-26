package com.animevost.sdk.parser

import com.animevost.sdk.model.UserProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class UserProfileParser {
    fun parse(
        html: String,
        pageUrl: String,
    ): UserProfile {
        val doc = Jsoup.parse(html, pageUrl)
        val form = doc.selectFirst("form#userinfo")

        return UserProfile(
            userId = form.inputValue("id")?.toIntOrNull(),
            username = usernameFromUrl(pageUrl),
            avatarUrl = doc
                .select(avatarSelector)
                .firstOrNull()
                ?.absUrl("src")
                ?.takeIf { it.isNotBlank() },
            allowHash = form.inputValue("dle_allow_hash"),
            fullName = form.inputValue("fullname"),
            location = form.inputValue("land"),
            email = form.inputValue("email"),
            info = form?.selectFirst("""textarea[name="info"]""")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() },
        )
    }

    private fun Element?.inputValue(name: String): String? =
        this?.selectFirst("""input[name="$name"]""")
            ?.`val`()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun usernameFromUrl(pageUrl: String): String {
        val path = runCatching { URI(pageUrl).path }.getOrDefault("")
        return path
            .trim('/')
            .split('/')
            .lastOrNull()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    private companion object {
        const val avatarSelector =
            ".loginAva img, div.ava img, div.avatar img, div.user-ava img, " +
                ".userinfo-ava img, div.img_profile img, img[src*=fotos], " +
                "img[src*=avatar], img[src*=/ava]"
    }
}
