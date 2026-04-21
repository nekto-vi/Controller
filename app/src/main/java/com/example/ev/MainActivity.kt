package com.example.ev

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.ev.databinding.ActivityMainBinding
import com.example.ev.notifications.ScenarioScheduleManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        ThemeHelper.updateTheme(newBase)
        val context = LocaleHelper.updateLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ScenarioScheduleManager.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        promptExactAlarmPermissionIfNeeded()

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val destinationId = intent.getIntExtra("destination", R.id.home)

        if (savedInstanceState == null) {
            val fragment = when (destinationId) {
                R.id.settings -> SettingsFragment()
                else -> HomeFragment()
            }
            replaceFragment(fragment)
            binding.bottomNavigationView.selectedItemId = destinationId
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10_001)
    }

    private fun promptExactAlarmPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        if (ScenarioScheduleManager.canScheduleExactAlarms(this)) return

        val prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("exact_alarm_prompt_shown", false)) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exact_alarm_permission_title))
            .setMessage(getString(R.string.exact_alarm_permission_message))
            .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                startActivity(ScenarioScheduleManager.createExactAlarmSettingsIntent(this))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        prefs.edit().putBoolean("exact_alarm_prompt_shown", true).apply()
    }
}