package me.kavishdevar.librepods.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.updates.GitHubReleaseHistoryRepository
import me.kavishdevar.librepods.data.updates.ReleaseHistoryEntry
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ReleaseHistoryScreen(onOpenRelease: (String) -> Unit) {
    var releases by remember { mutableStateOf<List<ReleaseHistoryEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val material = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (material) 16.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (material) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    val installedVersion = BuildConfig.VERSION_NAME.removeSuffix("-debug").removeSuffix("-play")

    LaunchedEffect(refreshKey) {
        loading = true
        failed = false
        try {
            releases = GitHubReleaseHistoryRepository.loadAll(forceRefresh = refreshKey > 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(topPadding))

        Text(
            text = stringResource(R.string.release_history_intro),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        when {
            loading -> StyledList {
                StyledListItem(
                    name = stringResource(R.string.release_history_loading),
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                )
            }

            failed -> {
                StyledList {
                    StyledListItem(
                        name = stringResource(R.string.release_history_title),
                        description = stringResource(R.string.release_history_load_failed),
                        onClick = null,
                        enabled = false,
                        orientation = ListItemOrientation.Vertical,
                    )
                    StyledListItem(
                        name = stringResource(R.string.release_history_retry),
                        onClick = { refreshKey++ },
                    )
                }
            }

            releases.isNullOrEmpty() -> StyledList {
                StyledListItem(
                    name = stringResource(R.string.release_history_title),
                    description = stringResource(R.string.release_history_empty),
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                )
            }

            else -> StyledList(title = stringResource(R.string.release_history_section)) {
                releases.orEmpty().forEach { release ->
                    val version = release.tag.removePrefix("v")
                    val descriptor = buildList {
                        if (version == installedVersion) add(stringResource(R.string.release_history_current_installed))
                        if (release.publishedAt.isNotBlank()) add(formatReleaseDate(release.publishedAt))
                        if (release.isPrerelease) add(stringResource(R.string.release_history_prerelease))
                    }.joinToString(" · ")

                    StyledListItem(
                        name = version,
                        description = descriptor.ifBlank { release.title },
                        onClick = { onOpenRelease(release.tag) },
                        orientation = ListItemOrientation.Vertical,
                    )
                }
            }
        }

        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
fun ReleaseHistoryDetailScreen(tag: String) {
    var release by remember(tag) { mutableStateOf<ReleaseHistoryEntry?>(null) }
    var loading by remember(tag) { mutableStateOf(true) }
    var failed by remember(tag) { mutableStateOf(false) }
    var refreshKey by remember(tag) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val material = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (material) 16.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (material) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

    LaunchedEffect(tag, refreshKey) {
        loading = true
        failed = false
        try {
            release = GitHubReleaseHistoryRepository.load(tag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(topPadding))

        when {
            loading -> StyledList {
                StyledListItem(
                    name = stringResource(R.string.release_history_loading),
                    onClick = null,
                    enabled = false,
                    orientation = ListItemOrientation.Vertical,
                )
            }

            failed -> {
                StyledList {
                    StyledListItem(
                        name = stringResource(R.string.release_history_title),
                        description = stringResource(R.string.release_history_load_failed),
                        onClick = null,
                        enabled = false,
                        orientation = ListItemOrientation.Vertical,
                    )
                    StyledListItem(
                        name = stringResource(R.string.release_history_retry),
                        onClick = { refreshKey++ },
                    )
                }
            }

            else -> {
                val entry = release
                if (entry != null) {
                    StyledList(title = stringResource(R.string.release_history_release)) {
                        StyledListItem(
                            name = entry.title,
                            description = buildList {
                                add(entry.tag)
                                if (entry.publishedAt.isNotBlank()) add(formatReleaseDate(entry.publishedAt))
                                if (entry.isPrerelease) add(stringResource(R.string.release_history_prerelease))
                            }.joinToString(" · "),
                            onClick = null,
                            enabled = false,
                            orientation = ListItemOrientation.Vertical,
                        )
                    }
                    StyledList {
                        StyledListItem(
                            name = stringResource(R.string.release_history_details),
                            description = entry.notes?.toReadableReleaseNotes()
                                ?.ifBlank { stringResource(R.string.release_history_no_notes) }
                                ?: stringResource(R.string.release_history_no_notes),
                            onClick = null,
                            enabled = false,
                            orientation = ListItemOrientation.Vertical,
                            descriptionMaxLines = Int.MAX_VALUE,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(bottomPadding))
    }
}

private fun formatReleaseDate(publishedAt: String): String {
    val date = publishedAt.substringBefore('T')
    return runCatching {
        LocalDate.parse(date).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()),
        )
    }.getOrDefault(date)
}

private fun String.toReadableReleaseNotes(): String = lineSequence()
    .map { line ->
        when {
            line.startsWith("### ") -> line.removePrefix("### ")
            line.startsWith("## ") -> line.removePrefix("## ")
            line.startsWith("# ") -> line.removePrefix("# ")
            line.startsWith("- ") || line.startsWith("* ") -> "• ${line.drop(2)}"
            else -> line
        }
            .replace(Regex("\\*\\*(.*?)\\*\\*")) { it.groupValues[1] }
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)")) { it.groupValues[1] }
    }
    .joinToString("\n")
    .trim()
