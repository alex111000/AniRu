package com.animevost.sdk.parser

import com.animevost.sdk.model.CommentAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsParserTest {

    private val parser = CommentsParser()

    @Test
    fun `parses detail page comments metadata and depth`() {
        val page = parser.parsePage(commentsPageHtml())

        assertEquals(3970, page.newsId)
        assertEquals("0123456789abcdef0123456789abcdef", page.allowHash)
        assertEquals(1, page.currentPage)
        assertEquals(3, page.totalPages)
        assertEquals(listOf(2048934, 2048924), page.comments.map { it.id })

        val first = page.comments.first()
        assertEquals("Deutscher Kater", first.author.name)
        assertEquals("https://animevost.org/user/Deutscher+Kater/", first.author.profileUrl)
        assertEquals("https://animevost.org/uploads/fotos/foto_455301.jpg", first.avatarUrl)
        assertEquals("Анимешники", first.userGroup)
        assertEquals("07.07.2026 19:17", first.createdAtLabel)
        assertEquals(309, first.authorCommentCount)
        assertEquals(46, first.ordinal)
        assertEquals(4, first.indentLevel)
        assertEquals(0, first.depth)
        assertFalse(first.isOnline ?: true)
        assertEquals("Что ж, меня заинтересовало. Буду продолжать смотреть", first.bodyText)
        assertEquals(emptyList(), first.quotes)
        assertEquals(setOf(CommentAction.REPLY, CommentAction.REPORT), first.actions)

        val reply = page.comments.last()
        assertEquals("Gaf", reply.author.name)
        assertEquals("Я спонсирую проект", reply.userGroup)
        assertEquals(9, reply.indentLevel)
        assertEquals(1, reply.depth)
        assertTrue(reply.isOnline ?: false)
        assertEquals(setOf(CommentAction.DELETE, CommentAction.EDIT, CommentAction.REPORT), reply.actions)
        assertEquals(
            "Помимо персоны уже были боле мение по луче озвучки так что не надо тут смотреть тайтл в озвучке персоны себя не уважать",
            reply.bodyText,
        )
    }

    @Test
    fun `parses nested DLE quote tree without mixing quote text into body`() {
        val comment = parser.parsePage(commentsPageHtml()).comments.last()

        assertEquals(1, comment.quotes.size)
        val outerQuote = comment.quotes.single()
        assertEquals("Emissar", outerQuote.authorName)
        assertEquals(
            "а не плохая серия, я вот посмотрел 2, и не вызывает отторжения, вполне норм ну ты и гнида",
            outerQuote.bodyText,
        )

        assertEquals(1, outerQuote.quotes.size)
        val nestedQuote = outerQuote.quotes.single()
        assertEquals("DreadCoffins", nestedQuote.authorName)
        assertEquals("этой твари персоне", nestedQuote.bodyText)
        assertEquals(emptyList(), nestedQuote.quotes)
    }

    @Test
    fun `parses ajax comments html with explicit page metadata`() {
        val page = parser.parseComments(
            html = """
                <div id='comment-id-2046102'>
                  <div class="commentContent_4">
                    <div class="commentFinal">
                      <div class="commentFinalAva" align="center">
                        <span><strong><a href="https://v13.vost.pw/user/den_play/">den_play</a></strong></span>
                        <img src="https://v13.vost.pw/uploads/fotos/foto_165200.jpg" alt=""/>
                        <span>Анимешники</span>
                      </div>
                      <div class="commentFinalData">01.07.2026 22:04 &nbsp; Комментарий: 2787 <span>#30</span></div>
                      <div class="commentFinalText">
                        <div id='comm-id-2046102'>ну... вроде норм <br>но пока точно проходник</div>
                      </div>
                      <div class="commentFinalIt"><span>Оффлайн</span></div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            newsId = 3970,
            currentPage = 2,
        )

        assertEquals(3970, page.newsId)
        assertEquals(2, page.currentPage)
        assertEquals(null, page.totalPages)
        assertEquals("den_play", page.comments.single().author.name)
        assertEquals("ну... вроде норм но пока точно проходник", page.comments.single().bodyText)
    }

    private fun commentsPageHtml(): String =
        """
            <html>
              <head><base href="https://animevost.org/" /></head>
              <body>
                <script>
                  var dle_news_id= '3970';
                  var dle_login_hash = '0123456789abcdef0123456789abcdef';
                  var total_comments_pages= '3';
                  var current_comments_page= '1';
                </script>
                <form method="post" action="" name="dlemasscomments" id="dlemasscomments">
                  <div id="dle-comments-list">
                    <div id="dle-ajax-comments"></div>
                    <div id='comment-id-2048934'>
                      <div class="commentContent_4">
                        <div class="commentFinal">
                          <div class="commentFinalAva" align="center">
                            <span><strong><a href="https://animevost.org/user/Deutscher+Kater/">Deutscher Kater</a></strong></span>
                            <img src="https://animevost.org/uploads/fotos/foto_455301.jpg" alt=""/>
                            <span>Анимешники</span>
                          </div>
                          <div class="commentFinalData">07.07.2026 19:17 &nbsp; Комментарий: 309 <span style="position: absolute; right: 5px;">#46</span></div>
                          <div class="commentFinalText">
                            <div id='comm-id-2048934'>Что ж, меня заинтересовало. Буду продолжать смотреть</div>
                          </div>
                          <div class="commentFinalIt">
                            <strong><a onmouseover="dle_copy_quote('Deutscher&nbsp;Kater');" href="#" onclick="dle_ins('2048934'); return false;">Ответить</a></strong>
                            <a href="javascript:AddComplaint('2048934', 'comments')">Жалоба</a>
                            <span style="color: #dd2020; position: absolute; right: 5px; padding-top:5px;">Оффлайн</span>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div id='comment-id-2048924'>
                      <div class="commentContent_9">
                        <div class="commentFinal">
                          <div class="commentFinalAva" align="center">
                            <span><strong><a href="https://animevost.org/user/Gaf/">Gaf</a></strong></span>
                            <img src="https://animevost.org/uploads/fotos/foto_75728.jpg" alt=""/>
                            <span>Я спонсирую проект</span>
                          </div>
                          <div class="commentFinalData">07.07.2026 18:44 &nbsp; Комментарий: 13968 <span style="position: absolute; right: 5px;">#44</span></div>
                          <div class="commentFinalText">
                            <div id='comm-id-2048924'>
                              <!--QuoteBegin Emissar --><div class="titlequote">Цитата: Emissar</div><div class="quote"><!--QuoteEBegin-->а не плохая серия, я вот посмотрел 2, и не вызывает отторжения, вполне норм<br><br><!--QuoteBegin DreadCoffins --><div class="titlequote">Цитата: DreadCoffins</div><div class="quote"><!--QuoteEBegin-->этой твари персоне<!--QuoteEnd--></div><!--QuoteEEnd--><br>ну ты и гнида<!--QuoteEnd--></div><!--QuoteEEnd-->
                              <br>Помимо персоны уже были боле мение по луче озвучки так что не надо тут смотреть тайтл в озвучке персоны себя не уважать
                            </div>
                          </div>
                          <div class="commentFinalIt">
                            <a href="javascript:ajax_comm_edit('2048924', 'comments')">Изменить</a>
                            <a href="javascript:AddComplaint('2048924', 'comments')">Жалоба</a>
                            <a href="javascript:DeleteComments('2048924', '0123456789abcdef0123456789abcdef')">Удалить</a>
                            <span style="color: #7ddd20; position: absolute; right: 5px; padding-top:5px;">Онлайн</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </form>
              </body>
            </html>
        """.trimIndent()
}
