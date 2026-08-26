package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.error.AnimeVostAuthException
import com.animevost.sdk.error.AnimeVostRegistrationException
import com.animevost.sdk.error.AnimeVostServerException
import com.animevost.sdk.http.AnimeVostHttpClient
import com.animevost.sdk.model.RegistrationRequest
import com.animevost.sdk.model.RegistrationStatus
import com.animevost.sdk.model.UserProfileUpdate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimeVostAuthClientTest {

    @Test
    fun `login posts DLE form and returns session from cookies`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = "<html>ok</html>",
            cookiesAfterPost = mapOf(
                "dle_user_id" to "42",
                "dle_password" to "password_hash",
                "dle_hash" to "session_hash",
            ),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val session = client.login(username = "test_user", password = "secret")

        assertEquals(listOf("https://example.test/animevost/index.php?do=login"), httpClient.postedUrls)
        assertEquals(
            mapOf(
                "login_name" to "test_user",
                "login_password" to "secret",
                "login" to "submit",
            ),
            httpClient.postedForms.single(),
        )
        assertEquals(42, session.userId)
        assertEquals("test_user", session.username)
        assertTrue(client.isLoggedIn())
        assertEquals(session, client.currentSession())
    }

    @Test
    fun `login rejects auth error response`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeAuthHttpClient(response = "<div class=\"berrors\">Ошибка авторизации</div>"),
        )

        assertFailsWith<AnimeVostAuthException> {
            client.login(username = "bad", password = "wrong")
        }
    }

    @Test
    fun `login rejects missing auth cookies`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeAuthHttpClient(response = "<html>ok</html>"),
        )

        assertFailsWith<AnimeVostAuthException> {
            client.login(username = "bad", password = "wrong")
        }
        assertFalse(client.isLoggedIn())
    }

    @Test
    fun `logout calls endpoint and clears local session`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = "<html>ok</html>",
            initialCookies = mapOf(
                "dle_user_id" to "42",
                "dle_password" to "password_hash",
                "dle_hash" to "session_hash",
            ),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        assertTrue(client.isLoggedIn())

        client.logout()

        assertEquals(listOf("https://example.test/animevost/index.php?action=logout"), httpClient.requestedUrls)
        assertTrue(httpClient.cookiesCleared)
        assertFalse(client.isLoggedIn())
    }

    @Test
    fun `getProfile fetches user page and parses profile`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = """
                <html>
                  <body>
                    <form id="userinfo">
                      <input name="id" value="42" />
                      <input name="fullname" value="Tester Name" />
                    </form>
                  </body>
                </html>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val profile = client.getProfile("test_user")

        assertEquals(listOf("https://example.test/animevost/user/test_user/"), httpClient.requestedUrls)
        assertEquals(42, profile.userId)
        assertEquals("test_user", profile.username)
        assertEquals("Tester Name", profile.fullName)
    }

    @Test
    fun `register accepts rules and posts live registration form`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = "<html>registered</html>",
            cookiesAfterPost = mapOf("dle_user_id" to "77"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.register(
            RegistrationRequest(
                username = "new_user",
                password = "secret123",
                email = "new@example.test",
            ),
        )

        assertEquals(
            listOf(
                "https://example.test/animevost/index.php?do=register",
                "https://example.test/animevost/index.php?do=register",
            ),
            httpClient.postedUrls,
        )
        assertEquals(
            mapOf(
                "do" to "register",
                "dle_rules_accept" to "yes",
            ),
            httpClient.postedForms[0],
        )
        assertEquals(
            mapOf(
                "submit_reg" to "submit_reg",
                "do" to "register",
                "name" to "new_user",
                "password1" to "secret123",
                "password2" to "secret123",
                "email" to "new@example.test",
            ),
            httpClient.postedForms[1],
        )
        assertEquals("new_user", result.username)
        assertEquals(RegistrationStatus.ACTIVE, result.status)
        assertEquals(77, result.session?.userId)
        assertEquals("new_user", result.session?.username)
    }

    @Test
    fun `register clears an existing authenticated session before opening form`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = "<html>registered</html>",
            initialCookies = mapOf(
                "dle_user_id" to "42",
                "dle_password" to "old_password_hash",
                "dle_hash" to "old_session_hash",
            ),
            cookiesAfterPost = mapOf("dle_user_id" to "77"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/"),
            httpClient = httpClient,
        )

        assertTrue(client.isLoggedIn())

        val result = client.register(
            RegistrationRequest(
                username = "new_user",
                password = "secret123",
                email = "new@example.test",
            ),
        )

        assertTrue(httpClient.cookiesCleared)
        assertTrue(httpClient.cookiesAtPost.first().isEmpty())
        assertEquals(77, result.session?.userId)
        assertEquals("new_user", result.session?.username)
    }

    @Test
    fun `register reports pending email activation when server requires confirmation`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = """
                <html>
                  <body>
                    <h1>Отправлен запрос на активацию</h1>
                    <p>Запрос на регистрацию принят.</p>
                    <p>Через 10 минут Вы получите письмо с инструкциями для следующего шага.</p>
                  </body>
                </html>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.register(
            RegistrationRequest(
                username = "worker_v2",
                password = "secret123",
                email = "worker@example.test",
            ),
        )

        assertEquals("worker_v2", result.username)
        assertEquals(RegistrationStatus.PENDING_EMAIL_ACTIVATION, result.status)
        assertEquals(null, result.session)
        assertFalse(client.isLoggedIn())
    }

    @Test
    fun `activateRegistration fetches email activation link from mirror`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(response = "<html>Ваш аккаунт активирован</html>")
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://animevost.org/"),
            httpClient = httpClient,
        )

        val result = client.activateRegistration(
            "https://v13.vost.pw/index.php?do=register&doaction=validating&id=fake_token",
        )

        assertEquals(
            listOf("https://v13.vost.pw/index.php?do=register&doaction=validating&id=fake_token"),
            httpClient.requestedUrls,
        )
        assertTrue(result.activated)
    }

    @Test
    fun `register rejects registration error response`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeAuthHttpClient(
                response = "<div class=\"berrors\">Это имя уже используется</div>",
            ),
        )

        val error = assertFailsWith<AnimeVostRegistrationException> {
            client.register(
                RegistrationRequest(
                    username = "new_user",
                    password = "secret123",
                    email = "new@example.test",
                ),
            )
        }
        assertEquals("Это имя уже используется", error.message)
    }

    @Test
    fun `updateProfile fetches current form and posts merged fields`(): Unit = runBlocking {
        val httpClient = FakeAuthHttpClient(
            response = """
                <html>
                  <body>
                    <form id="userinfo">
                      <input name="id" value="42" />
                      <input name="dle_allow_hash" value="hash_value" />
                      <input name="fullname" value="Old Name" />
                      <input name="land" value="Old Land" />
                      <input name="email" value="old@example.test" />
                      <textarea name="info">old info</textarea>
                    </form>
                  </body>
                </html>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val profile = client.updateProfile(
            username = "test_user",
            update = UserProfileUpdate(
                fullName = "New Name",
                email = "new@example.test",
            ),
        )

        assertEquals(listOf("https://example.test/animevost/user/test_user/"), httpClient.requestedUrls)
        assertEquals(listOf("https://example.test/animevost/user/test_user/"), httpClient.multipartUrls)
        assertEquals(
            mapOf(
                "doaction" to "adduserinfo",
                "id" to "42",
                "dle_allow_hash" to "hash_value",
                "fullname" to "New Name",
                "land" to "Old Land",
                "email" to "new@example.test",
                "info" to "old info",
            ),
            httpClient.multipartForms.single(),
        )
        assertEquals("test_user", profile.username)
    }

    @Test
    fun `updateProfile rejects profile without edit hash`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeAuthHttpClient(response = "<html><body></body></html>"),
        )

        assertFailsWith<AnimeVostAuthException> {
            client.updateProfile(
                username = "test_user",
                update = UserProfileUpdate(fullName = "New Name"),
            )
        }
    }

    @Test
    fun `updateProfile rejects DLE validation error returned with successful http status`(): Unit = runBlocking {
        val profileHtml = """
            <html>
              <body>
                <form id="userinfo">
                  <input name="id" value="42" />
                  <input name="dle_allow_hash" value="hash_value" />
                  <textarea name="info">old info</textarea>
                </form>
              </body>
            </html>
        """.trimIndent()
        val httpClient = FakeAuthHttpClient(
            response = profileHtml,
            multipartResponse = """
                <div class="berrors">Поле слишком длинное. Максимум 1000 символов.</div>
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val error = assertFailsWith<AnimeVostServerException> {
            client.updateProfile(
                username = "test_user",
                update = UserProfileUpdate(info = "new info"),
            )
        }

        assertTrue(error.serverMessage.contains("1000"))
    }

    @Test
    fun `updateProfile ignores unrelated pending publications notice`(): Unit = runBlocking {
        val profileHtml = """
            <html>
              <body>
                <form id="userinfo">
                  <input name="id" value="42" />
                  <input name="dle_allow_hash" value="hash_value" />
                  <textarea name="info">new info</textarea>
                </form>
              </body>
            </html>
        """.trimIndent()
        val httpClient = FakeAuthHttpClient(
            response = profileHtml,
            multipartResponse = """
                <div class="berrors">Информация Ваших публикаций, ожидающих модерации, нет</div>
                $profileHtml
            """.trimIndent(),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val profile = client.updateProfile(
            username = "test_user",
            update = UserProfileUpdate(info = "new info"),
        )

        assertEquals("test_user", profile.username)
        assertEquals("new info", profile.info)
    }

    private class FakeAuthHttpClient(
        private val response: String,
        initialCookies: Map<String, String> = emptyMap(),
        private val cookiesAfterPost: Map<String, String> = emptyMap(),
        private val multipartResponse: String = response,
    ) : AnimeVostHttpClient {
        val requestedUrls = mutableListOf<String>()
        val postedUrls = mutableListOf<String>()
        val postedForms = mutableListOf<Map<String, String>>()
        val cookiesAtPost = mutableListOf<Map<String, String>>()
        val multipartUrls = mutableListOf<String>()
        val multipartForms = mutableListOf<Map<String, String>>()
        var cookiesCleared = false
        private val cookies = initialCookies.toMutableMap()

        override suspend fun get(url: String, headers: Map<String, String>): String {
            requestedUrls += url
            return response
        }

        override suspend fun post(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String {
            postedUrls += url
            postedForms += form
            cookiesAtPost += cookies.toMap()
            cookies += cookiesAfterPost
            return response
        }

        override suspend fun postMultipart(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String {
            multipartUrls += url
            multipartForms += form
            return multipartResponse
        }

        override fun getCookie(name: String): String? =
            cookies[name]

        override fun clearCookies() {
            cookies.clear()
            cookiesCleared = true
        }
    }
}
