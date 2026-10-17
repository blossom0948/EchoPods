package me.kavishdevar.librepods.presentation.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import me.kavishdevar.librepods.R

/** A single-choice appearance switch shared by onboarding and release guides. */
@Composable
fun AppearanceStyleToggle() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    var androidStyle by remember(preferences) {
        mutableStateOf(preferences.getBoolean("m3e_enabled", false))
    }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "m3e_enabled") {
                androidStyle = prefs.getBoolean("m3e_enabled", false)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    TextButton(
        onClick = {
            androidStyle = !androidStyle
            preferences.edit { putBoolean("m3e_enabled", androidStyle) }
        }
    ) {
        Text(stringResource(if (androidStyle) R.string.appearance_style_android else R.string.appearance_style_ios))
    }
}
