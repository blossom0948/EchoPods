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
                text = stringResource(R.string.privacy_last_updated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.privacy_overview),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.privacy_overview_text),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_local_processing),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_third_party),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.privacy_third_party_text),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_github_reports),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.privacy_github_report_text),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_prefill_edit),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_project_repository),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.privacy_repository_text),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_github_issues),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.privacy_issue_prefill_intro),
                style = MaterialTheme.typography.bodyMedium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    stringResource(R.string.privacy_prefill_version),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.privacy_prefill_device),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.privacy_prefill_android),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = stringResource(R.string.privacy_issue_submission),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.privacy_payments),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.privacy_no_paid_features),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.privacy_payment_text),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.contact), style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.privacy_contact_text),
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
