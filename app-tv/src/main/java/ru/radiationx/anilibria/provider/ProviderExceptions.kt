package ru.radiationx.anilibria.provider

open class ProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ProviderUnavailableException(provider: ProviderId, cause: Throwable? = null) :
    ProviderException("${provider.uiName} is temporarily unavailable", cause)

class ProviderContentNotFoundException(provider: ProviderId, id: String) :
    ProviderException("${provider.uiName}: content not found ($id)")

class ProviderPlaybackException(provider: ProviderId, message: String) :
    ProviderException("${provider.uiName}: $message")
