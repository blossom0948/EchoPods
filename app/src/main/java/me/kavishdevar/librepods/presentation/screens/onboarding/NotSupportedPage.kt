package me.kavishdevar.librepods.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.AppInfoCard
import me.kavishdevar.librepods.presentation.components.DeviceInfoCard
import me.kavishdevar.librepods.presentation.components.StyledListItem

@Composable
fun NotSupportedPage(
    continueWithBasicFeatures: () -> Unit,
    continueWithCompatibilityOverride: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(42.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.device_bluetooth_support_limited),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.check_the_repository_for_more_info),
                style = MaterialTheme.typography.bodyMedium,
            )
            DeviceInfoCard()
            AppInfoCard()

            StyledListItem(
                name = stringResource(R.string.continue_with_basic_features),
                description = stringResource(R.string.continue_with_basic_features_description),
                onClick = continueWithBasicFeatures,
            )
            StyledListItem(
                name = stringResource(R.string.continue_with_compatibility_override),
                description = stringResource(R.string.continue_with_compatibility_override_description),
                onClick = continueWithCompatibilityOverride,
            )
        }
    }
}
