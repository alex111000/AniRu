package com.animevost.sdk.model

data class RegistrationRequest(
    val username: String,
    val password: String,
    val email: String,
)

data class RegistrationResult(
    val username: String,
    val status: RegistrationStatus,
    val session: AuthSession?,
)

enum class RegistrationStatus {
    ACTIVE,
    PENDING_EMAIL_ACTIVATION,
}

data class RegistrationActivationResult(
    val activated: Boolean,
)
