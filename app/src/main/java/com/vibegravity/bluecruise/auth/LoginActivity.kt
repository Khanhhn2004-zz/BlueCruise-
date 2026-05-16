package com.vibegravity.bluecruise.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.phoneEditText.doAfterTextChanged { editable ->
            viewModel.onPhoneChanged(editable?.toString().orEmpty())
        }
        binding.passwordEditText.doAfterTextChanged { editable ->
            viewModel.onPasswordChanged(editable?.toString().orEmpty())
        }
        binding.loginButton.setOnClickListener {
            viewModel.submit()
        }
        binding.forgotPasswordText.setOnClickListener {
            Toast.makeText(this, R.string.forgot_password, Toast.LENGTH_SHORT).show()
        }
        binding.signUpHereText.setOnClickListener {
            Toast.makeText(this, R.string.sign_up_here, Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: LoginUiState) {
        updateEditTextIfNeeded(binding.phoneEditText.text?.toString().orEmpty(), state.phone) {
            binding.phoneEditText.setText(it)
            binding.phoneEditText.setSelection(it.length)
        }
        updateEditTextIfNeeded(binding.passwordEditText.text?.toString().orEmpty(), state.password) {
            binding.passwordEditText.setText(it)
            binding.passwordEditText.setSelection(it.length)
        }

        binding.phoneInputLayout.error = state.phoneError
        binding.passwordInputLayout.error = state.passwordError
        binding.loginErrorText.text = state.submitError
        binding.loginErrorText.visibility = if (state.submitError.isNullOrBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.loginButton.isEnabled = !state.isLoading
        binding.loginButton.text = if (state.isLoading) {
            getString(R.string.logging_in)
        } else {
            getString(R.string.login_button)
        }

        if (state.navigateToMain) {
            viewModel.consumeNavigation()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }

    private inline fun updateEditTextIfNeeded(
        currentValue: String,
        newValue: String,
        applyUpdate: (String) -> Unit
    ) {
        if (currentValue != newValue) {
            applyUpdate(newValue)
        }
    }
}
