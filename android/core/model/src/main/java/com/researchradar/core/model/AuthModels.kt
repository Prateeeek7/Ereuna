package com.researchradar.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SignUpRequest(val name: String, val email: String, val password: String)

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class AccountUser(val id: String, val name: String, val email: String)

@Serializable
data class AuthResponse(val token: String, val user: AccountUser)
