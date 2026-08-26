package com.animevost.sdk.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileParserTest {

    private val parser = UserProfileParser()

    @Test
    fun `parses editable user profile form`() {
        val profile = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="loginAva">
                      <img src="/uploads/fotos/foto_42.jpg" />
                    </div>
                    <form id="userinfo">
                      <input name="id" value="42" />
                      <input name="dle_allow_hash" value="hash_value" />
                      <input name="fullname" value="Tester Name" />
                      <input name="land" value="Moon" />
                      <input name="email" value="tester@example.test" />
                      <textarea name="info">raw info</textarea>
                    </form>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://animevost.org/user/test_user/",
        )

        assertEquals(42, profile.userId)
        assertEquals("test_user", profile.username)
        assertEquals("https://animevost.org/uploads/fotos/foto_42.jpg", profile.avatarUrl)
        assertEquals("hash_value", profile.allowHash)
        assertEquals("Tester Name", profile.fullName)
        assertEquals("Moon", profile.location)
        assertEquals("tester@example.test", profile.email)
        assertEquals("raw info", profile.info)
        assertTrue(profile.canEdit)
    }

    @Test
    fun `returns public profile shell when edit form is missing`() {
        val profile = parser.parse(
            html = """
                <html>
                  <body>
                    <div class="userinfo-ava"><img src="/uploads/fotos/foto_7.jpg" /></div>
                  </body>
                </html>
            """.trimIndent(),
            pageUrl = "https://animevost.org/user/public_user/",
        )

        assertNull(profile.userId)
        assertEquals("public_user", profile.username)
        assertEquals("https://animevost.org/uploads/fotos/foto_7.jpg", profile.avatarUrl)
        assertNull(profile.allowHash)
        assertTrue(!profile.canEdit)
    }
}
