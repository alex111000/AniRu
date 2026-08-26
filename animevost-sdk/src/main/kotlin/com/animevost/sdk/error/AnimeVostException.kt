package com.animevost.sdk.error

sealed class AnimeVostException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AnimeVostNetworkException(
    cause: Throwable,
) : AnimeVostException("Network request failed", cause)

class AnimeVostHttpException(
    val statusCode: Int,
    message: String,
) : AnimeVostException("HTTP $statusCode: $message")

class AnimeVostAuthException(
    message: String,
) : AnimeVostException(message)

class AnimeVostRegistrationException(
    message: String,
) : AnimeVostException(message)

class AnimeVostValidationException(
    message: String,
) : AnimeVostException(message)

open class AnimeVostServerException(
    message: String,
    val serverMessage: String = message,
) : AnimeVostException(message)

class AnimeVostCaptchaException(
    message: String,
) : AnimeVostServerException(message)

class AnimeVostRateLimitException(
    message: String,
) : AnimeVostServerException(message)
