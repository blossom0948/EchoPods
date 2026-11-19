package me.kavishdevar.librepods.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import com.galaxyairpods.update.UpdateInfo
import com.galaxyairpods.update.UpdateManager
import com.galaxyairpods.update.UpdateState

@Composable
fun UpdatePromptDialog(
    enabled: Boolean,
    backdrop: LayerBackdrop,
) {
    if (!enabled || BuildConfig.STORE_BUILD) return

    val context = LocalContext.current
    val manager = remember(context) { UpdateManager.shared(context) }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dismissedInThisComposition by remember { mutableIntStateOf(-1) }

    val info = when (val current = state) {
        is UpdateState.Available -> current.info
        is UpdateState.Downloading -> current.info
        is UpdateState.Ready -> current.info
        is UpdateState.Installing -> current.info
        is UpdateState.Error -> current.info
        else -> null
    }
    var lastInfo by remember { androidx.compose.runtime.mutableStateOf<UpdateInfo?>(null) }
    SideEffect { if (info != null) lastInfo = info }
    val displayInfo = info ?: lastInfo
    val dismissed = info?.let {
        dismissedInThisComposition == it.versionCode || manager.isPromptDismissed(it)
    } ?: true
    val visible = info != null && !dismissed
    val canDismiss = state !is UpdateState.Downloading

    LaunchedEffect(visible, info?.versionCode) {
        if (visible) manager.cancelAvailableNotification()
    }

    val dismiss: () -> Unit = {
        info?.let {
            manager.dismissPrompt(it)
            dismissedInThisComposition = it.versionCode
        }
    }
    val startDownload: (UpdateInfo) -> Unit = { update ->
        scope.launch { manager.downloadAndInstall(update) }
    }

    BackHandler(enabled = visible && !canDismiss) { }

    when (LocalDesignSystem.current) {
        DesignSystem.Material -> if (visible) {
            MaterialUpdateDialog(
                state = state,
                info = info,
                canDismiss = canDismiss,
                onDismiss = dismiss,
                onDownload = { startDownload(info) },
                onInstall = { file -> manager.installReady(file) },
            )
        }
        DesignSystem.Apple -> displayInfo?.let { updateInfo ->
            GlassUpdateDialog(
                visible = visible,
                state = state,
                info = updateInfo,
                backdrop = backdrop,
                canDismiss = canDismiss,
                onDismiss = dismiss,
                onDownload = { startDownload(updateInfo) },
                onInstall = { file -> manager.installReady(file) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MaterialUpdateDialog(
    state: UpdateState,
    info: UpdateInfo,
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UpdatePromptText(info = info, state = state, centered = false)
                UpdatePromptStatus(state)
                UpdatePromptActions(
                    state = state,
                    onDismiss = onDismiss,
                    onDownload = onDownload,
                    onInstall = onInstall,
                    useGlassButtons = false,
                    buttonBackdrop = null,
                )
            }
        }
    }
}

@Composable
private fun GlassUpdateDialog(
    visible: Boolean,
    state: UpdateState,
    info: UpdateInfo,
    backdrop: LayerBackdrop,
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
) {
    val innerBackdrop = rememberLayerBackdrop()
    val darkTheme = isSystemInDarkTheme()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.96f, animationSpec = spring()),
        exit = fadeOut() + scaleOut(targetScale = 0.98f, animationSpec = spring()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 390.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        exportedBackdrop = innerBackdrop,
                        shape = { RoundedCornerShape(36.dp) },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(12f.dp.toPx(), 32f.dp.toPx(), true, chromaticAberration = false)
                        },
                        onDrawSurface = {
                            drawRect(
                                if (darkTheme) {
                                    Color(0xFF252525).copy(alpha = 0.46f)
                                } else {
                                    Color(0xFFF4F3F6).copy(alpha = 0.72f)
                                },
                            )
                        },
                    )
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_prompt_badge),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                UpdatePromptText(info = info, state = state, centered = true)
                UpdatePromptStatus(state)
                UpdatePromptActions(
                    state = state,
                    onDismiss = onDismiss,
                    onDownload = onDownload,
                    onInstall = onInstall,
                    useGlassButtons = true,
                    buttonBackdrop = innerBackdrop,
                )
                if (!canDismiss) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun UpdatePromptText(
    info: UpdateInfo,
    state: UpdateState,
    centered: Boolean,
) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = stringResource(R.string.update_prompt_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (state) {
                is UpdateState.Downloading -> stringResource(R.string.update_prompt_progress, state.progress)
                is UpdateState.Ready -> stringResource(R.string.update_prompt_ready)
                is UpdateState.Installing -> stringResource(R.string.update_prompt_installing)
                is UpdateState.Error -> stringResource(R.string.update_prompt_error)
                else -> stringResource(R.string.update_prompt_available, info.versionName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (state !is UpdateState.Downloading && state !is UpdateState.Installing) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.update_prompt_install_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun UpdatePromptStatus(state: UpdateState) {
    when (state) {
        is UpdateState.Downloading -> {
            val fraction = (state.progress / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        is UpdateState.Installing -> Text(
            text = stringResource(R.string.update_prompt_system_confirmation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        else -> Unit
    }
}

@Composable
private fun UpdatePromptActions(
    state: UpdateState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    useGlassButtons: Boolean,
    buttonBackdrop: LayerBackdrop?,
) {
    when (state) {
        is UpdateState.Available, is UpdateState.Error -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (useGlassButtons && buttonBackdrop != null) {
                    StyledButton(
                        onClick = onDismiss,
                        backdrop = buttonBackdrop,
                        modifier = Modifier.weight(0.82f),
                        materialButtonStyle = MaterialButtonStyle.Outlined,
                    ) {
                        Text(
                            stringResource(R.string.update_prompt_later),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                    StyledButton(
                        onClick = onDownload,
                        backdrop = buttonBackdrop,
                        modifier = Modifier.weight(1.18f),
                        surfaceColor = MaterialTheme.colorScheme.primary,
                        materialButtonStyle = MaterialButtonStyle.Filled,
                    ) {
                        Text(
                            stringResource(
                                if (state is UpdateState.Error) R.string.update_prompt_retry
                                else R.string.update_prompt_download,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.update_prompt_later),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1.25f),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            stringResource(
                                if (state is UpdateState.Error) R.string.update_prompt_retry
                                else R.string.update_prompt_download,
                            ),
                        )
                    }
                }
            }
        }
        is UpdateState.Downloading -> Text(
            text = stringResource(R.string.update_prompt_downloading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        is UpdateState.Ready -> UpdateActionButton(
            text = stringResource(R.string.retry_install),
            onClick = { onInstall(state.file) },
            useGlassButtons = useGlassButtons,
            backdrop = buttonBackdrop,
        )
        is UpdateState.Installing -> UpdateActionButton(
            text = stringResource(R.string.update_prompt_later),
            onClick = onDismiss,
            useGlassButtons = useGlassButtons,
            backdrop = buttonBackdrop,
        )
        else -> Unit
    }
}

@Composable
private fun UpdateActionButton(
    text: String,
    onClick: () -> Unit,
    useGlassButtons: Boolean,
    backdrop: LayerBackdrop?,
) {
    if (useGlassButtons && backdrop != null) {
        StyledButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = MaterialTheme.colorScheme.primary,
            materialButtonStyle = MaterialButtonStyle.Filled,
        ) {
            Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
    }
}
