package me.kavishdevar.librepods.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R

@Composable
fun PrivacyPolicyPage(
    onForward: () -> Unit
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
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Last updated: 16 October 2026",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "AirPods Galaxy does not collect, store, sell, or share personal information for advertising, analytics, tracking, or profiling. The app does not include analytics, advertising SDKs, or tracking services.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Bluetooth observations and app settings are processed on your device. The app contacts GitHub to check for releases and download an update when one is available. GitHub may receive normal request metadata such as your network address and app version.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Third Party Services",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "The app links to the AirPods Galaxy GitHub repository for source code and issue reports. It does not send diagnostics or device information automatically.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "GitHub issue reports",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "If you choose to create an issue from the app, your browser opens GitHub with the title and details you entered, plus the app version and device model for troubleshooting. Nothing is submitted until you review and submit it on GitHub.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "You can edit or remove any pre-filled information before submitting the issue.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Project repository",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "The app provides a link to the public AirPods Galaxy GitHub repository. Opening it is optional and subject to GitHub's privacy policy.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "GitHub Issues",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "When opening a GitHub issue from AirPods Galaxy, the app may pre-fill:",
                style = MaterialTheme.typography.bodyMedium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    "• AirPods Galaxy version name and version code",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Device manufacturer and model",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Android build information",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "This information is only sent to GitHub if you review and submit the issue.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Payments",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "No paid features",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "All app-level AirPods Galaxy features are available without payment or a subscription. The app does not process payments or collect billing information.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Contact", style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "For questions or privacy requests, open an issue at github.com/blossom0948/Galaxy-AirPods.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onForward,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.i_agree),
                    style = MaterialTheme.typography.labelMediumEmphasized
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
