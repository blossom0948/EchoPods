package me.kavishdevar.librepods.presentation.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem

private const val ECHO_HEALTH_PACKAGE = "com.galaxyairpods.health"
private const val ECHO_HEALTH_APK_URL =
    "https://github.com/blossom0948/EchoPods/releases/latest/download/echo-health.apk"

@Composable
fun LabsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installed by remember { mutableStateOf(isEchoHealthInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var activeDownloadId by remember { mutableLongStateOf(-1L) }
    val downloadManager = remember {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = isEchoHealthInstalled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, downloadManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != activeDownloadId) return
                downloading = false
                val query = DownloadManager.Query().setFilterById(completedId)
                downloadManager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, context.getString(R.string.echo_health_download_failed), Toast.LENGTH_LONG).show()
                        return
                    }
                }
                val apkUri = downloadManager.getUriForDownloadedFile(completedId)
                if (apkUri == null) {
                    Toast.makeText(context, context.getString(R.string.echo_health_download_failed), Toast.LENGTH_LONG).show()
                    return
                }
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(installIntent) }
                    .onFailure {
                        Toast.makeText(context, context.getString(R.string.echo_health_install_failed), Toast.LENGTH_LONG).show()
                    }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun installOrOpenEchoHealth() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(ECHO_HEALTH_PACKAGE)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return
        }
        if (downloading) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            Toast.makeText(context, context.getString(R.string.echo_health_allow_install), Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            val request = DownloadManager.Request(ECHO_HEALTH_APK_URL.toUri())
                .setTitle(context.getString(R.string.echo_health_name))
                .setDescription(context.getString(R.string.echo_health_downloading))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "echo-health.apk",
                )
            activeDownloadId = downloadManager.enqueue(request)
            downloading = true
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.echo_health_download_failed), Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.echo_health_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.echo_health_labs_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        StyledList(title = stringResource(R.string.echo_health_name)) {
            StyledListItem(
                name = stringResource(R.string.echo_health_labs_subtitle),
                description = stringResource(R.string.echo_health_labs_details),
                orientation = ListItemOrientation.Vertical,
                enabled = false,
            )
            StyledListItem(
                name = stringResource(
                    when {
                        installed -> R.string.echo_health_open
                        downloading -> R.string.echo_health_downloading
                        else -> R.string.echo_health_install
                    },
                ),
                description = stringResource(
                    if (installed) R.string.echo_health_installed
                    else R.string.echo_health_install_description,
                ),
                onClick = ::installOrOpenEchoHealth,
                enabled = !downloading,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.echo_health_data_note),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isEchoHealthInstalled(context: Context): Boolean =
    context.packageManager.getLaunchIntentForPackage(ECHO_HEALTH_PACKAGE) != null
