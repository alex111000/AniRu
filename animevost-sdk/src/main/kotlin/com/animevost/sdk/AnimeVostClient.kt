package com.animevost.sdk

import com.animevost.sdk.config.AnimeVostConfig
import com.animevost.sdk.error.AnimeVostAuthException
import com.animevost.sdk.error.AnimeVostCaptchaException
import com.animevost.sdk.error.AnimeVostRateLimitException
import com.animevost.sdk.error.AnimeVostRegistrationException
import com.animevost.sdk.error.AnimeVostServerException
import com.animevost.sdk.error.AnimeVostValidationException
import com.animevost.sdk.http.AnimeVostHttpClient
import com.animevost.sdk.http.OkHttpAnimeVostHttpClient
import com.animevost.sdk.model.AnimeDetails
import com.animevost.sdk.model.AnimePage
import com.animevost.sdk.model.AnimePreview
import com.animevost.sdk.model.AuthSession
import com.animevost.sdk.model.CommentActionResult
import com.animevost.sdk.model.CommentPage
import com.animevost.sdk.model.CommentReplyTemplate
import com.animevost.sdk.model.CommentSubmissionResult
import com.animevost.sdk.model.CatalogFilter
import com.animevost.sdk.model.FavoriteActionResult
import com.animevost.sdk.model.NavigationData
import com.animevost.sdk.model.PlaylistEpisode
import com.animevost.sdk.model.RatingVoteResult
import com.animevost.sdk.model.RegistrationActivationResult
import com.animevost.sdk.model.RegistrationRequest
import com.animevost.sdk.model.RegistrationResult
import com.animevost.sdk.model.RegistrationStatus
import com.animevost.sdk.model.ScheduleDay
import com.animevost.sdk.model.UserProfile
import com.animevost.sdk.model.UserProfileUpdate
import com.animevost.sdk.model.VideoSource
import com.animevost.sdk.parser.AnimeDetailsParser
import com.animevost.sdk.parser.AnimeListParser
import com.animevost.sdk.parser.CommentsParser
import com.animevost.sdk.parser.FavoritesParser
import com.animevost.sdk.parser.NavigationParser
import com.animevost.sdk.parser.RandomAnimeParser
import com.animevost.sdk.parser.ScheduleParser
import com.animevost.sdk.parser.UserProfileParser
import com.animevost.sdk.parser.VideoSourceParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AnimeVostClient(
    private val config: AnimeVostConfig = AnimeVostConfig(),
    private val httpClient: AnimeVostHttpClient = OkHttpAnimeVostHttpClient(),
    private val scheduleParser: ScheduleParser = ScheduleParser(),
    private val animeListParser: AnimeListParser = AnimeListParser(),
    private val animeDetailsParser: AnimeDetailsParser = AnimeDetailsParser(),
    private val videoSourceParser: VideoSourceParser = VideoSourceParser(),
    private val navigationParser: NavigationParser = NavigationParser(),
    private val randomAnimeParser: RandomAnimeParser = RandomAnimeParser(),
    private val userProfileParser: UserProfileParser = UserProfileParser(),
    private val favoritesParser: FavoritesParser = FavoritesParser(),
    private val commentsParser: CommentsParser = CommentsParser(),
) {
    private var currentUsername: String? = null
    private var currentLoginHash: String? = null

    suspend fun login(username: String, password: String): AuthSession {
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }

        val response = httpClient.post(
            url = URI(normalizedBaseUrl()).resolve("index.php?do=login").toString(),
            form = mapOf(
                "login_name" to username.trim(),
                "login_password" to password,
                "login" to "submit",
            ),
            headers = requestHeaders(),
        )

        rememberLoginHash(response)
        if (response.hasAuthError()) {
            clearLocalSession()
            throw AnimeVostAuthException("Invalid username or password")
        }

        val session = currentSession(username = username.trim())
            ?: run {
                clearLocalSession()
                throw AnimeVostAuthException("Login did not return auth cookies")
            }
        currentUsername = session.username
        return session
    }

    suspend fun logout() {
        try {
            httpClient.get(
                url = URI(normalizedBaseUrl()).resolve("index.php?action=logout").toString(),
                headers = requestHeaders(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        } finally {
            clearLocalSession()
        }
    }

    fun isLoggedIn(): Boolean =
        authUserId() != null

    fun currentSession(): AuthSession? =
        currentSession(username = currentUsername)

    suspend fun register(request: RegistrationRequest): RegistrationResult {
        val username = request.username.trim()
        val email = request.email.trim()
        require(username.isNotBlank()) { "username must not be blank" }
        require(request.password.isNotBlank()) { "password must not be blank" }
        require(email.isNotBlank()) { "email must not be blank" }

        // Registration must never inherit an authenticated session from a previous account.
        clearLocalSession()

        val registerUrl = URI(normalizedBaseUrl()).resolve("index.php?do=register").toString()
        httpClient.post(
            url = registerUrl,
            form = mapOf(
                "do" to "register",
                "dle_rules_accept" to "yes",
            ),
            headers = requestHeaders(),
        )
        val response = httpClient.post(
            url = registerUrl,
            form = mapOf(
                "submit_reg" to "submit_reg",
                "do" to "register",
                "name" to username,
                "password1" to request.password,
                "password2" to request.password,
                "email" to email,
            ),
            headers = requestHeaders(),
        )

        rememberLoginHash(response)
        if (response.hasRegistrationError()) {
            throw AnimeVostRegistrationException(
                response.serverErrorMessage() ?: "Registration failed",
            )
        }

        val session = currentSession(username = username)
        if (session != null) {
            currentUsername = username
        }
        return RegistrationResult(
            username = username,
            status = if (session != null) {
                RegistrationStatus.ACTIVE
            } else {
                RegistrationStatus.PENDING_EMAIL_ACTIVATION
            },
            session = session,
        )
    }

    suspend fun activateRegistration(activationUrl: String): RegistrationActivationResult {
        require(activationUrl.isNotBlank()) { "activationUrl must not be blank" }

        val response = httpClient.get(
            url = URI(normalizedBaseUrl()).resolve(activationUrl.trim()).toString(),
            headers = requestHeaders(),
        )
        if (response.hasRegistrationError()) {
            throw AnimeVostRegistrationException("Registration activation failed")
        }
        return RegistrationActivationResult(
            activated = response.hasActivationSuccess(),
        )
    }

    suspend fun getProfile(username: String? = currentUsername): UserProfile {
        val profileUsername = username
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("username must not be blank")

        val profileUrl = profileUrl(profileUsername)
        val html = httpClient.get(
            url = profileUrl,
            headers = requestHeaders(),
        )
        rememberLoginHash(html)
        return userProfileParser.parse(html, profileUrl)
    }

    suspend fun updateProfile(
        username: String? = currentUsername,
        update: UserProfileUpdate,
    ): UserProfile = updateProfile(
        current = getProfile(username),
        update = update,
    )

    suspend fun updateProfile(
        current: UserProfile,
        update: UserProfileUpdate,
    ): UserProfile {
        if (!current.canEdit) {
            throw AnimeVostAuthException("Profile is not editable")
        }

        val profileUrl = profileUrl(current.username)
        val response = httpClient.postMultipart(
            url = profileUrl,
            form = mapOf(
                "doaction" to "adduserinfo",
                "id" to current.userId.toString(),
                "dle_allow_hash" to current.allowHash.orEmpty(),
                "fullname" to (update.fullName ?: current.fullName).orEmpty(),
                "land" to (update.location ?: current.location).orEmpty(),
                "email" to (update.email ?: current.email).orEmpty(),
                "info" to (update.info ?: current.info).orEmpty(),
            ),
            headers = requestHeaders(),
        )
        rememberLoginHash(response)
        response.profileUpdateErrorMessage()?.let { message ->
            throw AnimeVostServerException(
                message = "Profile update was rejected",
                serverMessage = message,
            )
        }
        return userProfileParser.parse(response, profileUrl)
    }

    suspend fun getFavorites(page: Int = 1): AnimePage {
        requireAuthenticated()
        require(page >= 1) { "page must be greater than zero" }

        val baseUrl = normalizedBaseUrl()
        val favoritesUrl = URI(baseUrl).resolve("favorites/").toString()
        val html = httpClient.get(
            url = if (page == 1) favoritesUrl else "${favoritesUrl}page/$page/",
            headers = requestHeaders(),
        )
        rememberLoginHash(html)
        return favoritesParser.parse(html, baseUrl)
    }

    suspend fun addFavorite(newsId: Int): FavoriteActionResult =
        updateFavorite(newsId = newsId, action = "add", isFavorite = true)

    suspend fun removeFavorite(newsId: Int): FavoriteActionResult =
        updateFavorite(newsId = newsId, action = "del", isFavorite = false)

    suspend fun getComments(animeUrl: String, page: Int = 1): CommentPage {
        require(animeUrl.isNotBlank()) { "animeUrl must not be blank" }
        require(page >= 1) { "page must be greater than zero" }

        val baseUrl = normalizedBaseUrl()
        val requestUrl = resolveSiteUrl(animeUrl.trim())
        if (page > 1) {
            val newsId = extractNewsId(requestUrl)
                ?: throw IllegalArgumentException("animeUrl must contain news id")
            return getComments(newsId = newsId, page = page)
        }

        val html = httpClient.get(
            url = requestUrl,
            headers = requestHeaders(),
        )
        rememberLoginHash(html)
        val pageData = commentsParser.parsePage(
            html = html,
            pageUrl = requestUrl,
            baseUrl = baseUrl,
        )
        rememberLoginHash(pageData.allowHash)
        return pageData
    }

    suspend fun getComments(newsId: Int, page: Int = 1): CommentPage {
        require(newsId > 0) { "newsId must be greater than zero" }
        require(page >= 1) { "page must be greater than zero" }

        val baseUrl = normalizedBaseUrl()
        val response = httpClient.get(
            url = commentsAjaxUrl(baseUrl, newsId, page),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        return commentsParser.parseComments(
            html = extractAjaxCommentsHtml(response),
            newsId = newsId,
            currentPage = page,
            baseUrl = baseUrl,
        )
    }

    suspend fun addComment(
        newsId: Int,
        text: String,
        authorName: String? = currentUsername,
    ): CommentSubmissionResult {
        requireAuthenticated()
        require(newsId > 0) { "newsId must be greater than zero" }
        val commentText = text.trim()
        require(commentText.isNotBlank()) { "text must not be blank" }
        val name = authorName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("authorName must not be blank")

        val baseUrl = normalizedBaseUrl()
        val response = httpClient.post(
            url = URI(baseUrl).resolve("engine/ajax/addcomments.php").toString(),
            form = mapOf(
                "post_id" to newsId.toString(),
                "comments" to commentText,
                "name" to name,
                "mail" to "",
                "editor_mode" to "",
                "skin" to DLE_SKIN,
                "sec_code" to "",
                "question_answer" to "",
                "recaptcha_response_field" to "",
                "recaptcha_challenge_field" to "",
                "allow_subscribe" to "0",
            ),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        val comments = commentsParser.parseComments(
            html = extractAjaxCommentsHtml(response),
            newsId = newsId,
            baseUrl = baseUrl,
        ).comments

        return CommentSubmissionResult(
            newsId = newsId,
            comments = comments,
            rawMessage = response.trim().takeIf { comments.isEmpty() && it.isNotBlank() },
        )
    }

    suspend fun getCommentReplyTemplate(commentId: Int): CommentReplyTemplate {
        require(commentId > 0) { "commentId must be greater than zero" }

        val response = httpClient.get(
            url = URI(normalizedBaseUrl())
                .resolve("engine/ajax/quote.php?id=$commentId")
                .toString(),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        return CommentReplyTemplate(
            commentId = commentId,
            markup = response.trim(),
        )
    }

    suspend fun reportComment(commentId: Int, text: String): CommentActionResult {
        requireAuthenticated()
        require(commentId > 0) { "commentId must be greater than zero" }
        val reportText = text.trim()
        require(reportText.isNotBlank()) { "text must not be blank" }

        val response = httpClient.post(
            url = URI(normalizedBaseUrl()).resolve("engine/ajax/complaint.php").toString(),
            form = mapOf(
                "id" to commentId.toString(),
                "text" to reportText,
                "action" to "comments",
            ),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        return CommentActionResult(
            commentId = commentId,
            success = response.trim().equals("ok", ignoreCase = true),
            message = response.trim().takeUnless { it.equals("ok", ignoreCase = true) || it.isBlank() },
        )
    }

    suspend fun deleteComment(
        commentId: Int,
        allowHash: String? = currentLoginHash,
    ): CommentActionResult {
        requireAuthenticated()
        require(commentId > 0) { "commentId must be greater than zero" }
        val hash = allowHash
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw AnimeVostAuthException("DLE allow hash is required")

        val response = httpClient.get(
            url = URI(normalizedBaseUrl())
                .resolve("engine/ajax/deletecomments.php?id=$commentId&dle_allow_hash=${encode(hash)}")
                .toString(),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        val deletedId = response.trim().toIntOrNull()
        return CommentActionResult(
            commentId = commentId,
            success = deletedId == commentId,
            message = response.trim().takeIf { deletedId != commentId && it.isNotBlank() },
        )
    }

    suspend fun voteAnime(newsId: Int, rating: Int): RatingVoteResult {
        require(newsId > 0) { "newsId must be greater than zero" }
        if (rating !in MIN_RATING..MAX_RATING) {
            throw AnimeVostValidationException("rating must be between $MIN_RATING and $MAX_RATING")
        }

        val response = httpClient.get(
            url = URI(normalizedBaseUrl())
                .resolve("engine/ajax/rating.php?go_rate=$rating&news_id=$newsId&skin=$DLE_SKIN")
                .toString(),
            headers = ajaxHeaders(),
        )
        validateServerResponse(response)
        val json = parseJsonObject(response)
        if (json == null || json.get("success")?.asBoolean != true) {
            throw AnimeVostServerException(extractJsonError(json) ?: "Rating vote failed")
        }
        val ratingHtml = decodeDleHtml(json.get("rating")?.asString.orEmpty())
            .takeIf { it.isNotBlank() }
        return RatingVoteResult(
            newsId = newsId,
            submittedRating = rating,
            rating = parseRating(ratingHtml),
            voteCount = json.get("votenum")?.asString?.toIntOrNull(),
            ratingHtml = ratingHtml,
            success = true,
        )
    }

    suspend fun getSchedule(): List<ScheduleDay> {
        val html = httpClient.get(
            url = normalizedBaseUrl(),
            headers = requestHeaders(),
        )
        return scheduleParser.parse(html, normalizedBaseUrl())
    }

    suspend fun getAnimeList(
        page: Int = 1,
        filter: CatalogFilter = CatalogFilter(),
    ): AnimePage {
        require(page >= 1) { "page must be greater than zero" }

        val baseUrl = normalizedBaseUrl()
        val catalogUrl = catalogUrl(baseUrl, filter)
        // Normal AnimeVost catalog pages are already date-descending. Fetch them
        // directly: this avoids depending on the DLE sort POST for the most common
        // path used by the TV home screen and catalog.
        val needsSortPost = filter.sortBy != CatalogSort.DATE || filter.sortAscending
        if (needsSortPost) {
            // DLE stores the requested sort in cookies. The POST response itself is
            // not guaranteed to contain catalog HTML, so the real page is fetched
            // with GET below.
            httpClient.post(
                url = catalogUrl,
                form = catalogSortForm(filter),
                headers = requestHeaders() + mapOf("Referer" to catalogUrl),
            )
        }
        val pageUrl = if (page == 1) catalogUrl else "${catalogUrl}page/$page/"
        val html = httpClient.get(
            url = pageUrl,
            headers = requestHeaders() + mapOf("Referer" to catalogUrl),
        )
        return animeListParser.parse(html, baseUrl)
    }

    suspend fun getNavigation(): NavigationData {
        val baseUrl = normalizedBaseUrl()
        val html = httpClient.get(
            url = baseUrl,
            headers = requestHeaders(),
        )
        return navigationParser.parse(html, baseUrl)
    }

    suspend fun getRandomAnime(): AnimePreview? {
        val baseUrl = normalizedBaseUrl()
        val html = httpClient.get(
            url = URI(baseUrl).resolve("get_random_post.php").toString(),
            headers = requestHeaders(),
        )
        return randomAnimeParser.parse(html, baseUrl)
    }

    suspend fun searchAnime(query: String, page: Int = 1): AnimePage {
        require(query.isNotBlank()) { "query must not be blank" }
        require(page >= 1) { "page must be greater than zero" }

        val baseUrl = normalizedBaseUrl()
        val form = buildMap {
            put("subaction", "search")
            put("story", query.trim())
            if (page > 1) {
                put("result_from", ((page - 1) * SEARCH_PAGE_SIZE + 1).toString())
            }
        }
        val html = httpClient.post(
            url = URI(baseUrl).resolve("index.php?do=search").toString(),
            form = form,
            headers = requestHeaders(),
        )
        val parsed = animeListParser.parse(html, baseUrl)
        return parsed.copy(
            currentPage = page,
            totalPages = maxOf(parsed.totalPages, page),
        )
    }

    suspend fun getAnimeDetails(url: String): AnimeDetails {
        require(url.isNotBlank()) { "url must not be blank" }

        val baseUrl = normalizedBaseUrl()
        val requestUrl = resolveSiteUrl(url.trim())
        val html = httpClient.get(
            url = requestUrl,
            headers = requestHeaders(),
        )
        return animeDetailsParser.parse(
            html = html,
            pageUrl = requestUrl,
            baseUrl = baseUrl,
        )
    }

    suspend fun getVideoSources(videoId: String): List<VideoSource> {
        require(videoId.isNotBlank()) { "videoId must not be blank" }

        val requestUrl = URI(normalizedBaseUrl())
            .resolve("frame5.php?play=${encode(videoId.trim())}&old=1")
            .toString()
        val response = httpClient.get(
            url = requestUrl,
            headers = requestHeaders(),
        )
        return videoSourceParser.parse(response)
    }

    /**
     * Returns the complete episode playlist for a title. Unlike the HTML `var data` block,
     * this endpoint is suitable for very long shows (One Piece, Naruto, etc.).
     */
    suspend fun getPlaylist(animeId: Int): List<PlaylistEpisode> {
        require(animeId > 0) { "animeId must be greater than zero" }

        val response = httpClient.post(
            url = URI(normalizedApiBaseUrl()).resolve("v1/playlist").toString(),
            form = mapOf("id" to animeId.toString()),
            headers = requestHeaders(),
        )

        val root = JsonParser.parseString(response)
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                sequenceOf("data", "playlist", "items", "result")
                    .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                    .firstOrNull()
            }
            else -> null
        } ?: return emptyList()

        return buildList {
            array.forEachIndexed { index, element ->
                if (!element.isJsonObject) return@forEachIndexed
                val item = element.asJsonObject
                val name = item.stringValue("name")
                    ?: item.stringValue("title")
                    ?: "${index + 1} серия"
                val number = EPISODE_NUMBER_REGEX.find(name)?.value?.toIntOrNull()
                val hd = item.firstUrl("hd", "url", "src", "file")
                val standard = item.firstUrl("std", "sd", "low", "normal")
                val preview = item.firstUrl("preview", "poster", "image")
                if (hd.isNullOrBlank() && standard.isNullOrBlank()) return@forEachIndexed
                add(
                    PlaylistEpisode(
                        name = name,
                        number = number,
                        hdUrl = hd,
                        standardUrl = standard,
                        previewUrl = preview,
                    )
                )
            }
        }.sortedWith(compareBy<PlaylistEpisode> { it.number ?: Int.MAX_VALUE }.thenBy { it.name })
    }

    private fun normalizedBaseUrl(): String =
        config.baseUrl.trim().trimEnd('/') + "/"

    private fun normalizedApiBaseUrl(): String =
        config.apiBaseUrl.trim().trimEnd('/') + "/"

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.firstUrl(vararg names: String): String? =
        names.asSequence()
            .mapNotNull { stringValue(it) }
            .mapNotNull(::normalizeMediaUrl)
            .firstOrNull()

    private fun normalizeMediaUrl(value: String): String? = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> URI(normalizedBaseUrl()).resolve(value).toString()
        else -> null
    }

    private fun catalogUrl(baseUrl: String, filter: CatalogFilter): String {
        val path = filter.path
            ?.trim()
            ?.trimStart('/')
            ?.takeIf { it.isNotBlank() }
            ?: return baseUrl
        return resolveSiteUrl(path).trimEnd('/') + "/"
    }

    private fun resolveSiteUrl(value: String): String {
        val base = URI(normalizedBaseUrl())
        val resolved = base.resolve(value)
        require(
            resolved.scheme.equals(base.scheme, ignoreCase = true) &&
                resolved.host.equals(base.host, ignoreCase = true),
        ) { "URL must belong to the configured AnimeVost host" }
        return resolved.toString()
    }

    private fun catalogSortForm(filter: CatalogFilter): Map<String, String> {
        val sortScope = if (isMainCatalogFilter(filter)) "main" else "cat"
        return mapOf(
            "dlenewssortby" to filter.sortBy.dleField,
            "dledirection" to if (filter.sortAscending) "asc" else "desc",
            "set_new_sort" to "dle_sort_$sortScope",
            "set_direction_sort" to "dle_direction_$sortScope",
        )
    }

    private fun isMainCatalogFilter(filter: CatalogFilter): Boolean =
        filter.path
            ?.trim()
            ?.trim('/')
            .isNullOrBlank()

    private fun profileUrl(username: String): String =
        URI(normalizedBaseUrl())
            .resolve("user/${encodePathSegment(username)}/")
            .toString()

    private fun requestHeaders(): Map<String, String> =
        mapOf("User-Agent" to config.userAgent)

    private fun ajaxHeaders(): Map<String, String> =
        requestHeaders() + mapOf("X-Requested-With" to "XMLHttpRequest")

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun currentSession(username: String?): AuthSession? {
        val userId = authUserId() ?: return null
        return AuthSession(
            userId = userId,
            username = username,
        )
    }

    private fun authUserId(): Int? =
        httpClient.getCookie("dle_user_id")
            ?.takeIf { it != "deleted" }
            ?.toIntOrNull()

    private fun requireAuthenticated() {
        if (!isLoggedIn()) {
            throw AnimeVostAuthException("Authentication required")
        }
    }

    private fun commentsAjaxUrl(baseUrl: String, newsId: Int, page: Int): String =
        URI(baseUrl)
            .resolve("engine/ajax/comments.php?cstart=$page&news_id=$newsId&skin=$DLE_SKIN&massact=disable")
            .toString()

    private fun extractAjaxCommentsHtml(response: String): String {
        val trimmed = response.trim()
        if (!trimmed.startsWith("{")) return response

        return parseJsonObject(trimmed)
            ?.get("comments")
            ?.asString
            .orEmpty()
            .ifBlank { response }
    }

    private fun extractNewsId(value: String): Int? =
        newsIdRegex.find(value)?.groupValues?.get(1)?.toIntOrNull()

    private fun rememberLoginHash(html: String?) {
        val value = html?.trim().orEmpty()
        currentLoginHash = dleLoginHashRegex.find(value)
            ?.groupValues
            ?.get(1)
            ?: value.takeIf { dleLoginHashValueRegex.matches(it) }
            ?: currentLoginHash
    }

    private fun clearLocalSession() {
        httpClient.clearCookies()
        currentUsername = null
        currentLoginHash = null
    }

    private fun validateServerResponse(response: String) {
        val message = normalizeServerMessage(response)
        if (message.isBlank()) return

        when {
            message.equals("ok", ignoreCase = true) -> return
            message.equals("error", ignoreCase = true) -> throw AnimeVostServerException("Server returned error")
            message.contains("Hacking attempt", ignoreCase = true) ->
                throw AnimeVostServerException("Server rejected request as invalid", message)
            message.contains("captcha", ignoreCase = true) ||
                message.contains("код безопасности", ignoreCase = true) ->
                throw AnimeVostCaptchaException(message)
            message.contains("слишком часто", ignoreCase = true) ||
                message.contains("повторите попытку", ignoreCase = true) ->
                throw AnimeVostRateLimitException(message)
            message.contains("Данный раздел доступен только для зарегистрированных пользователей", ignoreCase = true) ->
                throw AnimeVostAuthException("Authentication required")
        }
    }

    private fun normalizeServerMessage(response: String): String {
        val trimmed = response.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("{") || trimmed.startsWith("<div id='comment-id-") || trimmed.startsWith("<div id=\"comment-id-")) {
            return ""
        }
        return Jsoup.parse(trimmed).text().ifBlank { trimmed }.trim()
    }

    private fun parseJsonObject(response: String): JsonObject? =
        runCatching {
            JsonParser.parseString(response.trim()).asJsonObject
        }.getOrNull()

    private fun extractJsonError(json: JsonObject?): String? =
        json?.get("error")?.asString
            ?: json?.get("message")?.asString

    private fun decodeDleHtml(value: String): String =
        value.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")

    private fun parseRating(html: String?): Double? {
        val percent = html
            ?.let { ratingWidthRegex.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            ?: return null
        return percent / 20.0
    }

    private suspend fun updateFavorite(
        newsId: Int,
        action: String,
        isFavorite: Boolean,
    ): FavoriteActionResult {
        requireAuthenticated()
        require(newsId > 0) { "newsId must be greater than zero" }

        httpClient.get(
            url = URI(normalizedBaseUrl())
                .resolve("index.php?do=favorites&doaction=$action&id=$newsId")
                .toString(),
            headers = requestHeaders(),
        )
        return FavoriteActionResult(
            newsId = newsId,
            isFavorite = isFavorite,
        )
    }

    private fun String.hasAuthError(): Boolean =
        contains("Ошибка авторизации") || contains("berrors")

    private fun String.hasRegistrationError(): Boolean =
        contains("Ошибка") ||
            contains("berrors") ||
            contains("уже используется", ignoreCase = true)

    private fun String.serverErrorMessage(): String? =
        Jsoup.parse(this)
            .selectFirst(".berrors")
            ?.text()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun String.profileUpdateErrorMessage(): String? =
        Jsoup.parse(this)
            .select(".berrors")
            .asSequence()
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .firstOrNull { message ->
                !message.contains(
                    "публикаций, ожидающих модерации",
                    ignoreCase = true,
                )
            }

    private fun String.hasActivationSuccess(): Boolean =
        contains("активирован", ignoreCase = true) ||
            contains("активация", ignoreCase = true)

    private companion object {
        const val SEARCH_PAGE_SIZE = 10
        const val DLE_SKIN = "AnimeVostNext5"
        const val MIN_RATING = 1
        const val MAX_RATING = 5
        val EPISODE_NUMBER_REGEX = Regex("\\d+")
        val newsIdRegex = Regex("""/(\d+)-[^/]+\.html(?:[#?].*)?$""")
        val dleLoginHashRegex = Regex("""var\s+dle_login_hash\s*=\s*['"]([^'"]+)['"]""")
        val dleLoginHashValueRegex = Regex("""[a-fA-F0-9]{32}""")
        val ratingWidthRegex = Regex("""width:\s*(\d+(?:\.\d+)?)%""")
    }
}
