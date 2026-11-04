package com.jaid.timity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaid.timity.data.TimityRepository
import com.jaid.timity.data.cloud.SyncManager
import com.jaid.timity.ui.App
import com.jaid.timity.ui.AppLocale
import com.jaid.timity.ui.TimityTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TimityRepository.get(application)
        // AppCompat has already applied the per-app locale to this activity's resources.
        AppLocale.current = resources.configuration.locales[0]
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            TimityTheme(mode = settings.theme) {
                App(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up changes made on another device, and push anything that failed while offline.
        SyncManager.requestSync()
    }

    companion object {
        /** Switches the app language; AppCompat recreates the activity and persists the choice. */
        fun setLanguage(tag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

        fun currentLanguage(): String =
            AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "en" }.substringBefore('-')
    }
}
