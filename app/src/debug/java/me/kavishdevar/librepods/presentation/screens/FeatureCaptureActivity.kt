package me.kavishdevar.librepods.presentation.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.galaxyairpods.update.UpdateInfo
import com.galaxyairpods.update.UpdateManager
import com.galaxyairpods.update.UpdateState
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.UpdatePromptDialog
import me.kavishdevar.librepods.presentation.components.StyledScaffold
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel
import me.kavishdevar.librepods.presentation.widgets.WidgetGlassBackground
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug-only screenshots of real app surfaces; widget and device states are synthetic. */
class FeatureCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        val screen = intent.getStringExtra(EXTRA_SCREEN).orEmpty()
        if (screen == SCREEN_UPDATE_PROMPT) injectSyntheticUpdateState()
        setContent {
            LibrePodsTheme(m3eEnabled = false) {
                when (screen) {
                    SCREEN_FINDER -> {
                        val viewModel = androidx.compose.runtime.remember { AirPodsViewModel() }
                        StyledScaffold(
                            title = stringResource(R.string.airpods_finder_title),
                            showBackButton = true,
                        ) {
                            AirPodsFinderScreen(viewModel)
                        }
                    }

                    SCREEN_TRANSLATION -> StyledScaffold(
                        title = stringResource(R.string.live_translation_title),
                        showBackButton = true,
                    ) {
                        LiveTranslationScreen()
                    }

                    SCREEN_WIDGET -> ConnectWidgetPreview()
                    SCREEN_UPDATE_PROMPT -> UpdatePromptPreview()
                    else -> StyledScaffold(
                        title = "[DEMO] AirPods Pro",
                    ) {
                        AirPodsSettingsScreenPreviewApple()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectSyntheticUpdateState() {
        runCatching {
            val manager = UpdateManager.shared(applicationContext)
            val stateField = UpdateManager::class.java.getDeclaredField("_state")
            stateField.isAccessible = true
            val state = stateField.get(manager) as MutableStateFlow<UpdateState>
            state.value = UpdateState.Available(
                UpdateInfo(
                    versionCode = 66,
                    versionName = "0.6.5.8",
                    apkUrl = "https://example.invalid/preview.apk",
                    sha256 = null,
                ),
            )
        }
    }

    companion object {
        const val EXTRA_SCREEN = "capture_screen"
        const val SCREEN_FINDER = "finder"
        const val SCREEN_TRANSLATION = "translation"
        const val SCREEN_WIDGET = "widget"
        const val SCREEN_UPDATE_PROMPT = "update_prompt"
    }
}

@androidx.compose.runtime.Composable
private fun ConnectWidgetPreview() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.connect_widget_preview_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(100.dp).widthIn(max = 380.dp),
                factory = { viewContext ->
                    LayoutInflater.from(viewContext)
                        .inflate(R.layout.connect_airpods_widget, null, false)
                        .apply {
                            findViewById<TextView>(R.id.connect_widget_title)
                                .text = viewContext.getString(R.string.connect_widget_title)
                            findViewById<TextView>(R.id.connect_widget_subtitle)
                                .text = viewContext.getString(
                                    R.string.connect_widget_tap_to_connect,
                                    "[DEMO] AirPods Pro",
                                )
                            findViewById<TextView>(R.id.connect_widget_state).apply {
                                text = viewContext.getString(R.string.connect_widget_connect)
                                setBackgroundResource(R.drawable.connect_widget_state_disconnected)
                                setTextColor(viewContext.getColor(R.color.connect_widget_disconnected_text))
                            }
                            findViewById<ImageView>(R.id.connect_widget_glass_background)
                                .setImageBitmap(WidgetGlassBackground.create(viewContext, 2.35f))
                        }
                },
            )
            Text(
                text = stringResource(R.string.connect_widget_preview_caption),
                modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpdatePromptPreview() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // Match MainActivity: the capture node is a background sibling, never an
        // ancestor of the popup that samples it (avoids a RenderNode self-cycle).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .layerBackdrop(backdrop),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "EchoPods",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "에어팟 설정과 주변 기능",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        UpdatePromptDialog(enabled = true, backdrop = backdrop)
    }
}
