package com.example.ev

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ev.auth.NicknameAuthMapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : AppCompatActivity() {

    private lateinit var authTitle: TextView
    private lateinit var nicknameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordConfirmLayout: TextInputLayout
    private lateinit var nicknameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordConfirmInput: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var toggleModeButton: MaterialButton

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var registerMode: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        ThemeHelper.updateTheme(newBase)
        val context = LocaleHelper.updateLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser != null) {
            openMainAndFinish()
            return
        }
        setContentView(R.layout.activity_auth)

        authTitle = findViewById(R.id.authTitle)
        nicknameLayout = findViewById(R.id.nicknameLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordConfirmLayout = findViewById(R.id.passwordConfirmLayout)
        nicknameInput = findViewById(R.id.nicknameInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordConfirmInput = findViewById(R.id.passwordConfirmInput)
        errorText = findViewById(R.id.authErrorText)
        progress = findViewById(R.id.authProgress)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        toggleModeButton = findViewById(R.id.toggleRegisterModeButton)

        loginButton.setOnClickListener { attemptLogin() }
        registerButton.setOnClickListener { attemptRegister() }
        toggleModeButton.setOnClickListener {
            registerMode = !registerMode
            updateModeUi()
            hideError()
        }

        updateModeUi()
    }

    private fun updateModeUi() {
        authTitle.setText(if (registerMode) R.string.auth_register_title else R.string.auth_title)
        passwordConfirmLayout.visibility = if (registerMode) View.VISIBLE else View.GONE
        loginButton.visibility = if (registerMode) View.GONE else View.VISIBLE
        registerButton.visibility = if (registerMode) View.VISIBLE else View.GONE
        toggleModeButton.text = getString(
            if (registerMode) R.string.auth_switch_to_login else R.string.auth_switch_to_register
        )
    }

    private fun attemptLogin() {
        hideError()
        val nickResult = NicknameAuthMapper.parse(nicknameInput.text?.toString().orEmpty())
        if (nickResult is NicknameAuthMapper.NicknameParseResult.Error) {
            showError(getString(nickResult.messageRes))
            return
        }
        nickResult as NicknameAuthMapper.NicknameParseResult.Ok
        val password = passwordInput.text?.toString().orEmpty()
        if (password.length < 6) {
            showError(getString(R.string.auth_password_too_short))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val email = NicknameAuthMapper.toSyntheticEmail(nickResult.normalized)
                auth.signInWithEmailAndPassword(email, password).await()
                openMainAndFinish()
            } catch (e: Exception) {
                showError(mapAuthMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun attemptRegister() {
        hideError()
        val nickResult = NicknameAuthMapper.parse(nicknameInput.text?.toString().orEmpty())
        if (nickResult is NicknameAuthMapper.NicknameParseResult.Error) {
            showError(getString(nickResult.messageRes))
            return
        }
        nickResult as NicknameAuthMapper.NicknameParseResult.Ok
        val password = passwordInput.text?.toString().orEmpty()
        val confirm = passwordConfirmInput.text?.toString().orEmpty()
        if (password.length < 6) {
            showError(getString(R.string.auth_password_too_short))
            return
        }
        if (password != confirm) {
            showError(getString(R.string.auth_password_mismatch))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val email = NicknameAuthMapper.toSyntheticEmail(nickResult.normalized)
                auth.createUserWithEmailAndPassword(email, password).await()
                val user = auth.currentUser ?: error("No user after registration")
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(nickResult.displayNickname)
                    .build()
                user.updateProfile(profile).await()
                openMainAndFinish()
            } catch (e: Exception) {
                showError(mapAuthMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun mapAuthMessage(e: Exception): String {
        if (e is FirebaseAuthException) {
            return when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.auth_error_nickname_taken)
                "ERROR_INVALID_EMAIL" -> getString(R.string.auth_nickname_invalid)
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_USER_NOT_FOUND",
                "ERROR_USER_DISABLED" -> getString(R.string.auth_error_invalid_credentials)
                "ERROR_WEAK_PASSWORD" -> getString(R.string.auth_error_weak_password)
                "ERROR_NETWORK_REQUEST_FAILED" -> getString(R.string.auth_error_network)
                else -> e.message?.takeIf { it.isNotBlank() } ?: getString(R.string.auth_error_generic)
            }
        }
        val msg = e.message.orEmpty()
        if (msg.contains("network", ignoreCase = true) || msg.contains("Unable to resolve host", ignoreCase = true)) {
            return getString(R.string.auth_error_network)
        }
        return msg.ifBlank { getString(R.string.auth_error_generic) }
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        registerButton.isEnabled = !loading
        toggleModeButton.isEnabled = !loading
        nicknameInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        passwordConfirmInput.isEnabled = !loading
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorText.visibility = View.GONE
    }
}
