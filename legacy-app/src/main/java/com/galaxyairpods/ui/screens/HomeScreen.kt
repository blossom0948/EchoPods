package com.galaxyairpods.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyairpods.domain.control.AirPodsControlState
import com.galaxyairpods.domain.control.AapControlId
import com.galaxyairpods.domain.control.CallControlConfiguration
import com.galaxyairpods.domain.control.CallPressCount
import com.galaxyairpods.domain.control.ListeningMode
import com.galaxyairpods.domain.control.StemPressActions
import com.galaxyairpods.domain.model.AirPodsConnectionState
import com.galaxyairpods.domain.model.AirPodsState
import com.galaxyairpods.domain.motion.ProductPose
import com.galaxyairpods.permissions.PermissionManager
import com.galaxyairpods.ui.components.BatteryGrid
import com.galaxyairpods.ui.components.ProductRenderer
import com.galaxyairpods.update.UpdateInfo
import com.galaxyairpods.update.UpdateState

@Composable
fun HomeScreen(
    state: AirPodsState,
    controlState: AirPodsControlState,
    stemPressActions: StemPressActions,
    updateState: UpdateState,
    onStartScanning: () -> Unit,
    onOpenSettings: () -> Unit,
    onListeningModeChange: (ListeningMode) -> Unit,
    onCallControlConfigurationChange: (CallControlConfiguration) -> Unit,
    onRenameAirPods: (String) -> Unit,
    onStartUpdate: (UpdateInfo) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissionsGranted = remember(permissionRefresh) {
        PermissionManager.allGranted(context, PermissionManager.bluetoothPermissions())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionRefresh++ }
    var renameDialog by remember { mutableStateOf(false) }
    var proposedName by remember(state.deviceName) { mutableStateOf(state.deviceName.orEmpty()) }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) onStartScanning()
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.deviceName?.takeIf(String::isNotBlank) ?: state.model.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "설정",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }

        // LibrePods keeps the connected home screen visually quiet. Only
        // surface a connection line when Android audio is not confirmed.
        if (state.connectionState != AirPodsConnectionState.ANDROID_CONNECTED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(start = 2.dp),
            ) {
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = CircleShape,
                    color = connectionColor(state.connectionState),
                ) {}
                Text(
                    text = state.connectionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!permissionsGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Bluetooth 권한이 필요합니다", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "주변 감지와 배터리 갱신에 필요합니다. 알림 접근은 미디어 자동 제어에만 사용됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(PermissionManager.runtimePermissions()) }) {
                        Text("권한 허용")
                    }
                }
            }
        }

        if (updateState is UpdateState.Available) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("새 버전 ${updateState.info.versionName}", style = MaterialTheme.typography.titleMedium)
                        Text("업데이트를 설치할 수 있습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onStartUpdate(updateState.info) }) { Text("업데이트") }
                }
            }
        }

        ProductRenderer(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            artworkHeight = 218.dp,
            reducedMotion = true,
            dashboardStyle = true,
            motionPose = ProductPose(
                lid = if (state.caseOpen == true) 1f else 0f,
                leftLift = 1f,
                rightLift = 1f,
                dock = 1f,
            ),
        )

        BatteryGrid(
            state = state,
            modifier = Modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .offset(y = (-5).dp)
                .align(Alignment.CenterHorizontally),
            staggerMs = 0L,
            reducedMotion = true,
            librePodsStyle = true,
        )

        Spacer(Modifier.height(56.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (controlState.aapReady) renameDialog = true },
            enabled = controlState.aapReady,
            shape = RoundedCornerShape(23.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("이름", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.deviceName?.takeIf(String::isNotBlank) ?: "AirPods 이름",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "이름 변경", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("듣기 모드")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            listeningModeDisplayOrder.forEach { mode ->
                ListeningModeTile(
                    mode = mode,
                    selected = controlState.listeningMode() == mode,
                    enabled = controlState.aapReady,
                    onClick = { onListeningModeChange(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTitle("AirPods 길게 누르기")
        StemActionRow("왼쪽", stemPressActions.leftLongPress.label, onOpenSettings)
        StemActionRow("오른쪽", stemPressActions.rightLongPress.label, onOpenSettings)

        SectionTitle("통화 제어")
        val callControls = CallControlConfiguration.fromWireValue(
            controlState.value(AapControlId.CALL_MANAGEMENT_CONFIG),
        )
        CallControlRow(
            title = "음소거 / 음소거 해제",
            selected = callControls.muteUnmutePress,
            known = callControls.isKnown,
            enabled = controlState.aapReady,
            onSelected = { press ->
                onCallControlConfigurationChange(callControls.copy(muteUnmutePress = press))
            },
        )
        CallControlRow(
            title = "통화 종료",
            selected = callControls.hangUpPress,
            known = callControls.isKnown,
            enabled = controlState.aapReady,
            onSelected = { press ->
                onCallControlConfigurationChange(
                    callControls.copy(
                        muteUnmutePress = if (press == CallPressCount.SINGLE) {
                            CallPressCount.DOUBLE
                        } else {
                            CallPressCount.SINGLE
                        },
                    ),
                )
            },
        )
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("AirPods 이름 변경") },
            text = {
                TextField(
                    value = proposedName,
                    onValueChange = { proposedName = it },
                    singleLine = true,
                    label = { Text("새 이름") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameAirPods(proposedName)
                        renameDialog = false
                    },
                    enabled = proposedName.isNotBlank() && controlState.aapReady,
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 5.dp, bottom = 1.dp),
    )
}

@Composable
private fun ListeningModeTile(
    mode: ListeningMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (mode) {
        ListeningMode.OFF -> Icons.AutoMirrored.Filled.VolumeOff
        ListeningMode.NOISE_CANCELLATION -> Icons.Filled.Headphones
        ListeningMode.TRANSPARENCY -> Icons.Filled.Hearing
        ListeningMode.ADAPTIVE -> Icons.Filled.AutoAwesome
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(31.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = mode.shortLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StemActionRow(title: String, value: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "동작 변경", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CallControlRow(
    title: String,
    selected: CallPressCount,
    known: Boolean,
    enabled: Boolean,
    onSelected: (CallPressCount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = true },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (known) selected.label else "AirPods에서 설정을 확인하지 못함",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                Icon(Icons.Filled.ChevronRight, contentDescription = "누르기 설정", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CallPressCount.entries.forEach { press ->
                        DropdownMenuItem(
                            text = { Text(press.label) },
                            onClick = {
                                expanded = false
                                onSelected(press)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun connectionColor(state: AirPodsConnectionState) = when (state) {
    AirPodsConnectionState.ANDROID_CONNECTED -> MaterialTheme.colorScheme.tertiary
    AirPodsConnectionState.NEARBY_ONLY,
    AirPodsConnectionState.OTHER_DEVICE_OR_CONNECTION_PENDING,
    -> MaterialTheme.colorScheme.primary
    AirPodsConnectionState.DISCONNECTED,
    AirPodsConnectionState.UNKNOWN,
    -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val listeningModeDisplayOrder = listOf(
    ListeningMode.OFF,
    ListeningMode.TRANSPARENCY,
    ListeningMode.ADAPTIVE,
    ListeningMode.NOISE_CANCELLATION,
)

private fun ListeningMode.shortLabel(): String = when (this) {
    ListeningMode.OFF -> "끔"
    ListeningMode.NOISE_CANCELLATION -> "노이즈 캔슬링"
    ListeningMode.TRANSPARENCY -> "투명도"
    ListeningMode.ADAPTIVE -> "적응형"
}
