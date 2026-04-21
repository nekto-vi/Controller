package com.example.ev.auth

import androidx.annotation.StringRes
import com.example.ev.R
import com.google.firebase.auth.FirebaseUser

object NicknameAuthMapper {

    const val SYNTHETIC_EMAIL_DOMAIN = "ev.invalid"

    private val nickPattern = Regex("^[a-zA-Z0-9_]{3,20}$")

    sealed class NicknameParseResult {
        data class Ok(val normalized: String, val displayNickname: String) : NicknameParseResult()
        data class Error(@StringRes val messageRes: Int) : NicknameParseResult()
    }

    fun parse(raw: String): NicknameParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return NicknameParseResult.Error(R.string.auth_username_empty)
        }
        if (!nickPattern.matches(trimmed)) {
            return NicknameParseResult.Error(R.string.auth_username_invalid)
        }
        val normalized = trimmed.lowercase()
        return NicknameParseResult.Ok(normalized = normalized, displayNickname = trimmed)
    }

    fun toSyntheticEmail(normalizedNickname: String): String =
        "$normalizedNickname@$SYNTHETIC_EMAIL_DOMAIN"

    fun displayNickname(user: FirebaseUser): String {
        user.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        val email = user.email ?: return "?"
        val suffix = "@$SYNTHETIC_EMAIL_DOMAIN"
        if (email.endsWith(suffix)) {
            return email.removeSuffix(suffix).ifBlank { "?" }
        }
        return email.substringBefore("@").ifBlank { "?" }
    }
}
