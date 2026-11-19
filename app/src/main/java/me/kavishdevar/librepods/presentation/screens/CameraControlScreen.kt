/*
    EchoPods - AirPods controls for Android
    Based on LibrePods (GPL-3.0-or-later).
*/

package me.kavishdevar.librepods.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.StemPressType
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

@Composable
fun CameraControlScreen(
    viewModel: AirPodsViewModel,
    onOpenCamera: () -> Unit,
) {
    val action by viewModel.cameraAction.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.camera_control_description),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StyledList(title = stringResource(R.string.camera_remote_action)) {
            StyledListItem(
                name = stringResource(R.string.camera_remote_off),
                description = stringResource(R.string.camera_remote_off_description),
                onClick = { viewModel.setCameraAction(null) },
                orientation = ListItemOrientation.Vertical,
                selected = action == null,
                trailingContent = { RadioButton(selected = action == null, onClick = null) },
            )
            StyledListItem(
                name = stringResource(R.string.camera_remote_single_press),
                description = stringResource(R.string.camera_remote_single_press_description),
                onClick = { viewModel.setCameraAction(StemPressType.SINGLE_PRESS) },
                orientation = ListItemOrientation.Vertical,
                selected = action == StemPressType.SINGLE_PRESS,
                trailingContent = {
                    RadioButton(selected = action == StemPressType.SINGLE_PRESS, onClick = null)
                },
            )
            StyledListItem(
                name = stringResource(R.string.camera_remote_long_press),
                description = stringResource(R.string.camera_remote_long_press_description),
                onClick = { viewModel.setCameraAction(StemPressType.LONG_PRESS) },
                orientation = ListItemOrientation.Vertical,
                selected = action == StemPressType.LONG_PRESS,
                trailingContent = {
                    RadioButton(selected = action == StemPressType.LONG_PRESS, onClick = null)
                },
            )
        }
        StyledList {
            StyledListItem(
                modifier = Modifier.fillMaxWidth(),
                name = stringResource(R.string.camera_remote_open),
                description = stringResource(R.string.camera_remote_open_description),
                onClick = onOpenCamera,
                orientation = ListItemOrientation.Vertical,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
