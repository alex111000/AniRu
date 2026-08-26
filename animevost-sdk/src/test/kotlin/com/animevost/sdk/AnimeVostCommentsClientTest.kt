package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.error.AnimeVostAuthException
import com.animevost.sdk.error.AnimeVostServerException
import com.animevost.sdk.http.AnimeVostHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimeVostCommentsClientTest {

    @Test
    fun `getComments fetches detail page and parses embedded comments`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(getResponse = detailPageHtml())
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.getComments("tip/tv/3970-test.html")

        assertEquals(listOf("https://example.test/animevost/tip/tv/3970-test.html"), httpClient.requestedUrls)
        assertEquals(3970, page.newsId)
        assertEquals("0123456789abcdef0123456789abcdef", page.allowHash)
        assertEquals(1, page.currentPage)
        assertEquals(3, page.totalPages)
        assertEquals("worker_v1", page.comments.single().author.name)
    }

    @Test
    fun `getComments fetches ajax comments by news id`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(getResponse = ajaxResponse(commentHtml()))
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val page = client.getComments(newsId = 3970, page = 2)

        assertEquals(
            listOf("https://example.test/animevost/engine/ajax/comments.php?cstart=2&news_id=3970&skin=AnimeVostNext5&massact=disable"),
            httpClient.requestedUrls,
        )
        assertEquals(3970, page.newsId)
        assertEquals(2, page.currentPage)
        assertEquals(null, page.totalPages)
        assertEquals(2046102, page.comments.single().id)
    }

    @Test
    fun `addComment posts DLE form and parses returned comments`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(
            postResponse = ajaxResponse(commentHtml()),
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.addComment(
            newsId = 3970,
            text = "  Тестовый комментарий  ",
            authorName = "worker_v1",
        )

        assertEquals(
            listOf("https://example.test/animevost/engine/ajax/addcomments.php"),
            httpClient.postedUrls,
        )
        assertEquals(
            mapOf(
                "post_id" to "3970",
                "comments" to "Тестовый комментарий",
                "name" to "worker_v1",
                "mail" to "",
                "editor_mode" to "",
                "skin" to "AnimeVostNext5",
                "sec_code" to "",
                "question_answer" to "",
                "recaptcha_response_field" to "",
                "recaptcha_challenge_field" to "",
                "allow_subscribe" to "0",
            ),
            httpClient.postedForms.single(),
        )
        assertEquals(3970, result.newsId)
        assertEquals(null, result.rawMessage)
        assertEquals(2046102, result.comments.single().id)
    }

    @Test
    fun `addComment requires auth session`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeCommentsHttpClient(postResponse = ajaxResponse(commentHtml())),
        )

        assertFailsWith<AnimeVostAuthException> {
            client.addComment(newsId = 3970, text = "test", authorName = "worker_v1")
        }
    }

    @Test
    fun `getCommentReplyTemplate fetches DLE quote markup`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(getResponse = "[quote=worker_v1]text[/quote]")
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.getCommentReplyTemplate(commentId = 2048934)

        assertEquals(
            listOf("https://example.test/animevost/engine/ajax/quote.php?id=2048934"),
            httpClient.requestedUrls,
        )
        assertEquals(2048934, result.commentId)
        assertEquals("[quote=worker_v1]text[/quote]", result.markup)
    }

    @Test
    fun `reportComment posts complaint form`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(
            postResponse = "ok",
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        val result = client.reportComment(commentId = 2048934, text = "Нарушение правил")

        assertEquals(
            listOf("https://example.test/animevost/engine/ajax/complaint.php"),
            httpClient.postedUrls,
        )
        assertEquals(
            mapOf(
                "id" to "2048934",
                "text" to "Нарушение правил",
                "action" to "comments",
            ),
            httpClient.postedForms.single(),
        )
        assertEquals(2048934, result.commentId)
        assertEquals(true, result.success)
        assertEquals(null, result.message)
    }

    @Test
    fun `deleteComment uses remembered allow hash from comments page`(): Unit = runBlocking {
        val httpClient = FakeCommentsHttpClient(
            getResponse = detailPageHtml(),
            cookies = mapOf("dle_user_id" to "42"),
        )
        val client = AnimeVostClient(
            config = AnimeVostConfig(baseUrl = "https://example.test/animevost/"),
            httpClient = httpClient,
        )

        client.getComments("tip/tv/3970-test.html")
        val result = client.deleteComment(commentId = 2048934)

        assertEquals(
            listOf(
                "https://example.test/animevost/tip/tv/3970-test.html",
                "https://example.test/animevost/engine/ajax/deletecomments.php?id=2048934&dle_allow_hash=0123456789abcdef0123456789abcdef",
            ),
            httpClient.requestedUrls,
        )
        assertEquals(2048934, result.commentId)
        assertEquals(true, result.success)
    }

    @Test
    fun `comment actions map server rejection to sdk exception`(): Unit = runBlocking {
        val client = AnimeVostClient(
            httpClient = FakeCommentsHttpClient(
                postResponse = "Hacking attempt!",
                cookies = mapOf("dle_user_id" to "42"),
            ),
        )

        assertFailsWith<AnimeVostServerException> {
            client.reportComment(commentId = 2048934, text = "test")
        }
    }

    private fun detailPageHtml(): String =
        """
            <html>
              <body>
                <script>
                  var dle_news_id= '3970';
                  var dle_login_hash = '0123456789abcdef0123456789abcdef';
                  var total_comments_pages= '3';
                  var current_comments_page= '1';
                </script>
                ${commentHtml(id = 2048934, author = "worker_v1")}
              </body>
            </html>
        """.trimIndent()

    private fun commentHtml(
        id: Int = 2046102,
        author: String = "den_play",
    ): String =
        """
            <div id='comment-id-$id'>
              <div class="commentContent_4">
                <div class="commentFinal">
                  <div class="commentFinalAva" align="center">
                    <span><strong><a href="https://v13.vost.pw/user/$author/">$author</a></strong></span>
                    <img src="https://v13.vost.pw/uploads/fotos/foto_165200.jpg" alt=""/>
                    <span>Анимешники</span>
                  </div>
                  <div class="commentFinalData">01.07.2026 22:04 &nbsp; Комментарий: 2787 <span>#30</span></div>
                  <div class="commentFinalText">
                    <div id='comm-id-$id'>ну... вроде норм <br>но пока точно проходник</div>
                  </div>
                  <div class="commentFinalIt"><span>Оффлайн</span></div>
                </div>
              </div>
            </div>
        """.trimIndent()

    private fun ajaxResponse(commentsHtml: String): String =
        """{"navigation":"","comments":"${commentsHtml.toJsonStringContent()}"}"""

    private fun String.toJsonStringContent(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

    private class FakeCommentsHttpClient(
        private val getResponse: String = "",
        private val postResponse: String = "",
        private val cookies: Map<String, String> = emptyMap(),
    ) : AnimeVostHttpClient {
        val requestedUrls = mutableListOf<String>()
        val postedUrls = mutableListOf<String>()
        val postedForms = mutableListOf<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>): String {
            requestedUrls += url
            return if (url.contains("deletecomments.php")) {
                url.substringAfter("id=").substringBefore("&")
            } else {
                getResponse
            }
        }

        override suspend fun post(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String {
            postedUrls += url
            postedForms += form
            return postResponse
        }

        override suspend fun postMultipart(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): String = postResponse

        override fun getCookie(name: String): String? =
            cookies[name]

        override fun clearCookies() = Unit
    }
}
