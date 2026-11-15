package com.galaxyairpods.health

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

data class WorkoutLocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
)

data class WorkoutLap(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val activeDurationMillis: Long,
    val distanceMeters: Double,
)

data class WorkoutTrackingData(
    val workoutType: String? = null,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val distanceMeters: Double = 0.0,
    val currentSpeedMetersPerSecond: Double? = null,
    val averageSpeedMetersPerSecond: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val lastLocationAtEpochMillis: Long? = null,
    val locationStatus: String = "OFF",
    val route: List<WorkoutLocationSample> = emptyList(),
    val laps: List<WorkoutLap> = emptyList(),
    val lapAnchorStartedAtEpochMillis: Long? = null,
    val lapAnchorElapsedMillis: Long = 0L,
    val lapAnchorDistanceMeters: Double = 0.0,
)

/** GPS is optional: indoor workouts keep their timer and heart-rate recording without location. */
object WorkoutLocationTracker {
    private const val PREFS = "workout_location"
    private const val KEY_STATE = "active_route_v1"
    private const val MAX_ROUTE_POINTS = 1_500
    private val mutableState = MutableStateFlow(WorkoutTrackingData())
    val state = mutableState.asStateFlow()
    @Volatile private var skipNextDistanceSegment = false

    @Synchronized
    fun initialize(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null)
        mutableState.value = decode(saved)
    }

    @Synchronized
    fun start(context: Context, type: WorkoutType, withGps: Boolean, startedAtEpochMillis: Long = System.currentTimeMillis()) {
        skipNextDistanceSegment = false
        val initial = WorkoutTrackingData(
            workoutType = type.key,
            isTracking = withGps,
            locationStatus = if (withGps) "WAITING" else "NOT_RECORDED",
            lapAnchorStartedAtEpochMillis = startedAtEpochMillis,
        )
        mutableState.value = initial
        persist(context, initial)
        if (withGps) {
            val intent = Intent(context, WorkoutLocationForegroundService::class.java)
                .setAction(WorkoutLocationForegroundService.ACTION_START)
                .putExtra(WorkoutLocationForegroundService.EXTRA_WORKOUT_TYPE, type.key)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun pause(context: Context) {
        if (mutableState.value.isTracking) {
            context.startService(Intent(context, WorkoutLocationForegroundService::class.java).setAction(WorkoutLocationForegroundService.ACTION_PAUSE))
        }
    }

    fun resume(context: Context) {
        if (mutableState.value.isTracking) {
            context.startService(Intent(context, WorkoutLocationForegroundService::class.java).setAction(WorkoutLocationForegroundService.ACTION_RESUME))
        }
    }

    @Synchronized
    fun recordLap(context: Context, activeElapsedMillis: Long, now: Long = System.currentTimeMillis()): WorkoutLap? {
        val old = mutableState.value
        if (old.workoutType == null || old.isPaused) return null
        val lapStart = old.lapAnchorStartedAtEpochMillis ?: now
        val lapDuration = (activeElapsedMillis - old.lapAnchorElapsedMillis).coerceAtLeast(0L)
        val lapDistance = (old.distanceMeters - old.lapAnchorDistanceMeters).coerceAtLeast(0.0)
        if (lapDuration < MIN_LAP_DURATION_MS && lapDistance < MIN_LAP_DISTANCE_METERS) return null
        val lap = WorkoutLap(lapStart, now.coerceAtLeast(lapStart), lapDuration, lapDistance)
        val next = old.copy(
            laps = old.laps + lap,
            lapAnchorStartedAtEpochMillis = lap.endedAtEpochMillis,
            lapAnchorElapsedMillis = activeElapsedMillis,
            lapAnchorDistanceMeters = old.distanceMeters,
        )
        mutableState.value = next
        persist(context, next)
        return lap
    }

    @Synchronized
    fun finish(context: Context, activeElapsedMillis: Long = 0L): WorkoutTrackingData {
        val state = mutableState.value
        if (!state.isPaused && state.workoutType != null) {
            recordLap(context, activeElapsedMillis)
        }
        val result = mutableState.value
        context.stopService(Intent(context, WorkoutLocationForegroundService::class.java))
        mutableState.value = WorkoutTrackingData()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_STATE).apply()
        return result
    }

    internal fun setPaused(context: Context, paused: Boolean) {
        if (!paused) skipNextDistanceSegment = true
        val next = mutableState.value.copy(
            isPaused = paused,
            currentSpeedMetersPerSecond = null,
            locationStatus = if (paused) "PAUSED" else "SEARCHING",
        )
        mutableState.value = next
        persist(context, next)
    }

    internal fun setUnavailable(context: Context) {
        val next = mutableState.value.copy(locationStatus = "UNAVAILABLE")
        mutableState.value = next
        persist(context, next)
    }

    @Synchronized
    internal fun addLocation(context: Context, location: Location) {
        val old = mutableState.value
        if (!old.isTracking || old.isPaused || !location.hasAccuracy() || location.accuracy > MAX_ACCURACY_METERS) return
        val point = WorkoutLocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            accuracyMeters = location.accuracy,
            capturedAtEpochMillis = location.time,
        )
        val previous = old.route.lastOrNull()
        var distance = old.distanceMeters
        var speed: Double? = null
        var route = old.route
        if (previous != null) {
            val from = Location("echohealth").apply {
                latitude = previous.latitude
                longitude = previous.longitude
            }
            val segment = from.distanceTo(location).toDouble()
            val seconds = ((point.capturedAtEpochMillis - previous.capturedAtEpochMillis) / 1_000.0).coerceAtLeast(0.1)
            val candidateSpeed = segment / seconds
            val maximum = if (old.workoutType == WorkoutType.CYCLING.key) MAX_CYCLING_SPEED_MPS else MAX_WALK_RUN_SPEED_MPS
            if (skipNextDistanceSegment) {
                skipNextDistanceSegment = false
                route = (route + point).takeLast(MAX_ROUTE_POINTS)
            } else if (segment >= MIN_ROUTE_SEGMENT_METERS && candidateSpeed <= maximum) {
                distance += segment
                speed = candidateSpeed
                route = (route + point).takeLast(MAX_ROUTE_POINTS)
            } else if (candidateSpeed <= maximum) {
                speed = candidateSpeed.takeIf { segment >= MIN_SPEED_SEGMENT_METERS }
            } else {
                // A large GPS jump is treated as a bad fix; it never inflates the workout distance.
                return
            }
        } else {
            route = listOf(point)
        }
        val next = old.copy(
            distanceMeters = distance,
            currentSpeedMetersPerSecond = speed,
            averageSpeedMetersPerSecond = distance.takeIf { it > 0.0 }?.let { total ->
                total / elapsedTrackingSeconds(route.firstOrNull()?.capturedAtEpochMillis ?: point.capturedAtEpochMillis, point.capturedAtEpochMillis)
            },
            locationAccuracyMeters = point.accuracyMeters,
            lastLocationAtEpochMillis = point.capturedAtEpochMillis,
            locationStatus = "RECORDING",
            route = route,
        )
        mutableState.value = next
        if (route.size % 5 == 0 || route.size == 1) persist(context, next)
    }

    private fun elapsedTrackingSeconds(start: Long, end: Long): Double =
        max(1.0, (end - start) / 1_000.0)

    private fun persist(context: Context, data: WorkoutTrackingData) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATE, encode(data)).apply()
    }

    private fun encode(data: WorkoutTrackingData): String = JSONObject().apply {
        put("type", data.workoutType)
        put("distance", data.distanceMeters)
        put("status", data.locationStatus)
        put("last_location_at", data.lastLocationAtEpochMillis ?: JSONObject.NULL)
        put("tracking", data.isTracking)
        put("paused", data.isPaused)
        put("route", JSONArray().apply {
            data.route.forEach { point ->
                put(JSONObject().apply {
                    put("lat", point.latitude)
                    put("lon", point.longitude)
                    put("alt", point.altitudeMeters ?: JSONObject.NULL)
                    put("accuracy", point.accuracyMeters.toDouble())
                    put("time", point.capturedAtEpochMillis)
                })
            }
        })
        put("lap_anchor_started_at", data.lapAnchorStartedAtEpochMillis ?: JSONObject.NULL)
        put("lap_anchor_elapsed", data.lapAnchorElapsedMillis)
        put("lap_anchor_distance", data.lapAnchorDistanceMeters)
        put("laps", JSONArray().apply {
            data.laps.forEach { lap ->
                put(JSONObject().apply {
                    put("start", lap.startedAtEpochMillis)
                    put("end", lap.endedAtEpochMillis)
                    put("active_duration", lap.activeDurationMillis)
                    put("distance", lap.distanceMeters)
                })
            }
        })
    }.toString()

    private fun decode(encoded: String?): WorkoutTrackingData {
        if (encoded.isNullOrBlank()) return WorkoutTrackingData()
        return runCatching {
            val json = JSONObject(encoded)
            val routeJson = json.optJSONArray("route") ?: JSONArray()
            val route = (0 until routeJson.length()).mapNotNull { index ->
                val point = routeJson.optJSONObject(index) ?: return@mapNotNull null
                val lat = point.optDouble("lat", Double.NaN)
                val lon = point.optDouble("lon", Double.NaN)
                val time = point.optLong("time")
                if (!lat.isFinite() || !lon.isFinite() || time <= 0L) return@mapNotNull null
                WorkoutLocationSample(
                    lat,
                    lon,
                    point.takeIf { !it.isNull("alt") }?.optDouble("alt"),
                    point.optDouble("accuracy", 0.0).toFloat(),
                    time,
                )
            }.takeLast(MAX_ROUTE_POINTS)
            val lapsJson = json.optJSONArray("laps") ?: JSONArray()
            val laps = (0 until lapsJson.length()).mapNotNull { index ->
                val lap = lapsJson.optJSONObject(index) ?: return@mapNotNull null
                val start = lap.optLong("start")
                val end = lap.optLong("end")
                if (start <= 0L || end < start) return@mapNotNull null
                WorkoutLap(
                    startedAtEpochMillis = start,
                    endedAtEpochMillis = end,
                    activeDurationMillis = lap.optLong("active_duration").coerceAtLeast(0L),
                    distanceMeters = lap.optDouble("distance", 0.0).coerceAtLeast(0.0),
                )
            }.take(MAX_LAPS)
            WorkoutTrackingData(
                workoutType = json.optString("type").takeIf { it.isNotBlank() },
                distanceMeters = json.optDouble("distance", 0.0).coerceAtLeast(0.0),
                locationStatus = json.optString("status", "WAITING"),
                lastLocationAtEpochMillis = json.optLong("last_location_at", 0L).takeIf { it > 0L },
                laps = laps,
                lapAnchorStartedAtEpochMillis = json.optLong("lap_anchor_started_at", 0L).takeIf { it > 0L },
                lapAnchorElapsedMillis = json.optLong("lap_anchor_elapsed", 0L).coerceAtLeast(0L),
                lapAnchorDistanceMeters = json.optDouble("lap_anchor_distance", 0.0).coerceAtLeast(0.0),
                route = route,
                isTracking = json.optBoolean("tracking", route.isNotEmpty()),
                isPaused = json.optBoolean("paused", false),
            )
        }.getOrElse { WorkoutTrackingData() }
    }

    private const val MAX_ACCURACY_METERS = 50f
    private const val MIN_ROUTE_SEGMENT_METERS = 2.0
    private const val MIN_SPEED_SEGMENT_METERS = 1.0
    private const val MAX_WALK_RUN_SPEED_MPS = 12.0
    private const val MAX_CYCLING_SPEED_MPS = 45.0
    private const val MIN_LAP_DURATION_MS = 5_000L
    private const val MIN_LAP_DISTANCE_METERS = 5.0
    private const val MAX_LAPS = 500
}

class WorkoutLocationForegroundService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private var workoutType = WorkoutType.WALKING.key

    override fun onCreate() {
        super.onCreate()
        WorkoutLocationTracker.initialize(applicationContext)
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_RESTORE) {
            ACTION_RESTORE -> {
                val state = WorkoutLocationTracker.state.value
                if (state.isTracking) {
                    workoutType = state.workoutType ?: WorkoutType.WALKING.key
                    startForegroundCompat()
                    if (!state.isPaused) startLocationUpdates()
                } else {
                    stopSelf()
                }
            }
            ACTION_START -> {
                workoutType = intent?.getStringExtra(EXTRA_WORKOUT_TYPE) ?: WorkoutType.WALKING.key
                startForegroundCompat()
                startLocationUpdates()
            }
            ACTION_PAUSE -> {
                stopLocationUpdates()
                WorkoutLocationTracker.setPaused(this, true)
                startForegroundCompat()
            }
            ACTION_RESUME -> {
                WorkoutLocationTracker.setPaused(this, false)
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            WorkoutLocationTracker.setUnavailable(this)
            return
        }
        runCatching {
            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                WorkoutLocationTracker.setUnavailable(this)
                return
            }
            locationManager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, MIN_UPDATE_DISTANCE_METERS, this)
            updateNotification()
        }.onFailure { WorkoutLocationTracker.setUnavailable(this) }
    }

    private fun stopLocationUpdates() {
        runCatching { locationManager.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        WorkoutLocationTracker.addLocation(this, location)
        updateNotification()
    }

    override fun onProviderDisabled(provider: String) {
        WorkoutLocationTracker.setUnavailable(this)
        updateNotification()
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) startLocationUpdates()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val data = WorkoutLocationTracker.state.value
        val distance = if (data.distanceMeters >= 1_000) "%.2f km".format(data.distanceMeters / 1_000.0) else "%.0f m".format(data.distanceMeters)
        val title = if (data.isPaused) "운동 일시정지" else "운동 기록 중"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("$distance · Echo Health")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "운동 기록", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.galaxyairpods.health.location.START"
        const val ACTION_PAUSE = "com.galaxyairpods.health.location.PAUSE"
        const val ACTION_RESUME = "com.galaxyairpods.health.location.RESUME"
        const val ACTION_STOP = "com.galaxyairpods.health.location.STOP"
        private const val ACTION_RESTORE = "com.galaxyairpods.health.location.RESTORE"
        const val EXTRA_WORKOUT_TYPE = "workout_type"
        private const val CHANNEL_ID = "echo_health_workout"
        private const val NOTIFICATION_ID = 3101
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 2f
    }
}
