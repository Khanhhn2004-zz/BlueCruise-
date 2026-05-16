package com.vibegravity.bluecruise.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LaunchGateActivity : AppCompatActivity() {

    private val viewModel: LaunchGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch_gate)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.destination.collect { destination ->
                    when (destination) {
                        LaunchGateDestination.Login -> navigateTo(LoginActivity::class.java)
                        LaunchGateDestination.Main -> navigateTo(MainActivity::class.java)
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(
            Intent(this, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
