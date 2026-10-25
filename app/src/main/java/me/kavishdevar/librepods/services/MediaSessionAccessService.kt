package me.kavishdevar.librepods.services

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController as AndroidMediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import me.kavishdevar.librepods.utils.MediaSessionCandidate
import me.kavishdevar.librepods.utils.MediaSessionSelectionPolicy
import me.kavishdevar.librepods.utils.MediaController

/** Notification access is required by Android to resolve and control active media sessions. */
class MediaSessionAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaSessionResolver.attach(this)
    }

    override fun onListenerDisconnected() {
        MediaSessionResolver.detach(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        MediaSessionResolver.detach(this)
        super.onDestroy()
    }
}

object MediaSessionResolver {
    enum class ResumeResult {
        RESUMED,
        NO_PAUSED_SESSION,
        STALE_OR_UNSUPPORTED_SESSION,
    }

    private const val TAG = "MediaSessionResolver"

    @Volatile
    private var listener: MediaSessionAccessService? = null

    @Volatile
    private var manager: MediaSessionManager? = null

    @Volatile
    private var pausedToken: MediaSession.Token? = null

    @Volatile
    private var pausedPackageName: String? = null

    private var pausedController: AndroidMediaController? = null
    private var pausedControllerCallback: AndroidMediaController.Callback? = null
    private var pausedSessionConfirmed = false
    private val handler = Handler(Looper.getMainLooper())

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            validatePausedSession(controllers.orEmpty())
        }

    @Synchronized
    fun attach(service: MediaSessionAccessService) {
        if (listener === service) return
        detach(listener)
        listener = service
        manager = service.getSystemService(MediaSessionManager::class.java)
        runCatching {
            manager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(service, MediaSessionAccessService::class.java),
                handler,
            )
        }.onFailure { Log.w(TAG, "Unable to observe active media sessions", it) }
        Log.i(TAG, "Notification-listener media-session access connected")
    }

    @Synchronized
    fun detach(service: MediaSessionAccessService?) {
        if (service == null || listener !== service) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        clearPausedSession()
        manager = null
        listener = null
        Log.i(TAG, "Notification-listener media-session access disconnected")
    }

    fun isConnected(): Boolean = listener != null

    fun hasPlayingSession(): Boolean = activeControllers()?.any {
        it.playbackState?.state == MediaSessionSelectionPolicy.STATE_PLAYING
    } == true

    @Synchronized
    fun pausePlayingSession(): Boolean {
        val controllers = activeControllers() ?: return false
        val candidate = MediaSessionSelectionPolicy.selectPlaying(
            controllers.map { controller ->
                MediaSessionCandidate(
                    id = controller.sessionToken.hashCode().toString(),
                    playbackState = controller.playbackState?.state,
                    supportedActions = controller.playbackState?.actions ?: 0L,
                    lastPositionUpdateTime = controller.playbackState?.lastPositionUpdateTime ?: 0L,
                )
            }
        ) ?: return false

        val controller = controllers.firstOrNull {
            it.sessionToken.hashCode().toString() == candidate.id
        } ?: return false

        clearPausedSession()
        pausedToken = controller.sessionToken
        pausedPackageName = controller.packageName
        pausedController = controller
        pausedSessionConfirmed = false
        val token = controller.sessionToken
        val callback = object : AndroidMediaController.Callback() {
            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                if (pausedToken != token) return
                when (state?.state) {
                    android.media.session.PlaybackState.STATE_PAUSED -> pausedSessionConfirmed = true
                    android.media.session.PlaybackState.STATE_PLAYING -> {
                        if (pausedSessionConfirmed) clearPausedSession(cancelAutoResume = true)
                    }
                    android.media.session.PlaybackState.STATE_STOPPED,
                    android.media.session.PlaybackState.STATE_NONE,
                    android.media.session.PlaybackState.STATE_ERROR -> clearPausedSession(cancelAutoResume = true)
                    else -> Unit
                }
            }

            override fun onSessionDestroyed() {
                if (pausedToken == token) clearPausedSession(cancelAutoResume = true)
            }
        }
        pausedControllerCallback = callback
        controller.registerCallback(callback, handler)
        try {
            controller.transportControls.pause()
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Pause command failed for active media session", exception)
            clearPausedSession()
            return false
        }
        handler.postDelayed({
            if (pausedToken == token && !pausedSessionConfirmed) {
                if (pausedController?.playbackState?.state == MediaSessionSelectionPolicy.STATE_PAUSED) {
                    pausedSessionConfirmed = true
                } else {
                    clearPausedSession(cancelAutoResume = true)
                }
            }
        }, 2_000L)
        Log.i(TAG, "Pause sent to active session package=${controller.packageName}")
        return true
    }

    @Synchronized
    fun resumePausedSession(): ResumeResult {
        val token = pausedToken ?: return ResumeResult.NO_PAUSED_SESSION
        val controllers = activeControllers() ?: return ResumeResult.STALE_OR_UNSUPPORTED_SESSION
        val controller = controllers.firstOrNull { it.sessionToken == token }
            ?: return ResumeResult.STALE_OR_UNSUPPORTED_SESSION.also { clearPausedSession(cancelAutoResume = true) }
        val playback = controller.playbackState
        if (playback?.state != MediaSessionSelectionPolicy.STATE_PAUSED ||
            playback.actions and MediaSessionSelectionPolicy.ACTION_PLAY == 0L
        ) {
            clearPausedSession(cancelAutoResume = true)
            return ResumeResult.STALE_OR_UNSUPPORTED_SESSION
        }

        try {
            controller.transportControls.play()
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Play command failed for previously paused media session", exception)
            clearPausedSession(cancelAutoResume = true)
            return ResumeResult.STALE_OR_UNSUPPORTED_SESSION
        }
        Log.i(TAG, "Play sent to previously paused session package=${pausedPackageName ?: controller.packageName}")
        clearPausedSession()
        return ResumeResult.RESUMED
    }

    private fun activeControllers(): List<AndroidMediaController>? {
        val service = listener ?: return null
        val sessionManager = manager ?: return null
        return try {
            sessionManager.getActiveSessions(
                ComponentName(service, MediaSessionAccessService::class.java)
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification access is not active; using media-key fallback")
            null
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Active media sessions are temporarily unavailable", exception)
            null
        }
    }

    @Synchronized
    private fun validatePausedSession(controllers: List<AndroidMediaController>) {
        val token = pausedToken ?: return
        val controller = controllers.firstOrNull { it.sessionToken == token }
        if (controller == null) {
            clearPausedSession(cancelAutoResume = true)
            return
        }
        val otherSessionPlaying = controllers.any {
            it.sessionToken != token && it.playbackState?.state == MediaSessionSelectionPolicy.STATE_PLAYING
        }
        val state = controller.playbackState?.state
        if (otherSessionPlaying || state == android.media.session.PlaybackState.STATE_STOPPED ||
            state == android.media.session.PlaybackState.STATE_NONE ||
            (pausedSessionConfirmed && state == MediaSessionSelectionPolicy.STATE_PLAYING)
        ) {
            clearPausedSession(cancelAutoResume = true)
        }
    }

    @Synchronized
    private fun clearPausedSession(cancelAutoResume: Boolean = false) {
        pausedControllerCallback?.let { callback ->
            runCatching { pausedController?.unregisterCallback(callback) }
        }
        pausedControllerCallback = null
        pausedController = null
        pausedToken = null
        pausedPackageName = null
        pausedSessionConfirmed = false
        if (cancelAutoResume) MediaController.cancelAutoResumeAfterExternalMediaChange()
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.className == MediaSessionAccessService::class.java.name }
    }
}
