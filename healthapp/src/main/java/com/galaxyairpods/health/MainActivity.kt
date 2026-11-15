package com.galaxyairpods.health

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.pm.PackageManager
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionWorkoutStateHolder.initialize(applicationContext)
        HealthWorkoutSessionStore.initialize(applicationContext)
        WorkoutLocationTracker.initialize(applicationContext)
        applyAppearanceIntent(intent)
        setContent { EchoPodsHealthApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAppearanceIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        sendBridgeRequest(this, HealthWorkoutBridgeReceiver.ACTION_QUERY_STATE)
        sendBridgeRequest(this, HealthWorkoutBridgeReceiver.ACTION_QUERY_APPEARANCE)
        lifecycleScope.launch { HealthConnectTodayReader.refresh(applicationContext) }
    }

    private fun applyAppearanceIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_APPEARANCE_MATERIAL) == true) {
            CompanionWorkoutStateHolder.updateAppearance(
                applicationContext,
                intent.getBooleanExtra(EXTRA_APPEARANCE_MATERIAL, false),
            )
        }
    }

    companion object {
        const val EXTRA_APPEARANCE_MATERIAL = "appearance_material"
    }
}

private enum class HealthTab { TODAY, WORKOUT, HISTORY }
private const val PREF_DAILY_STEP_GOAL = "daily_step_goal"
private const val DEFAULT_DAILY_STEP_GOAL = 8_000

@Composable
private fun EchoPodsHealthApp() {
    val context = LocalContext.current
    val workoutState by CompanionWorkoutStateHolder.state.collectAsState()
    val history by WorkoutHistoryStore.history.collectAsState()
    val healthOverview by HealthConnectTodayReader.state.collectAsState()
    val activeHealthWorkout by HealthWorkoutSessionStore.session.collectAsState()
    val locationTracking by WorkoutLocationTracker.state.collectAsState()
    val useMaterial = workoutState.useMaterialAppearance
    val echoPodsInstalled = remember {
        context.packageManager.getLaunchIntentForPackage(HealthWorkoutBridgeReceiver.ECHOPODS_PACKAGE) != null
    }
    HealthTheme(useMaterialAppearance = useMaterial) {
        HealthSystemBars()
        HealthWorkoutContent(
            workoutState = workoutState,
            history = history,
            useMaterial = useMaterial,
            healthOverview = healthOverview,
            echoPodsInstalled = echoPodsInstalled,
            activeHealthWorkout = activeHealthWorkout,
            locationTracking = locationTracking,
        )
    }
}

@Composable
private fun HealthSystemBars() {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    SideEffect {
        context.findActivity()?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
private fun HealthWorkoutContent(
    workoutState: CompanionWorkoutState,
    history: List<SavedWorkout>,
    useMaterial: Boolean,
    healthOverview: HealthConnectTodayState,
    echoPodsInstalled: Boolean,
    activeHealthWorkout: ActiveHealthWorkout?,
    locationTracking: WorkoutTrackingData,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isKorean = remember { Locale.getDefault().language == "ko" }
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val preferences = remember(context) {
        context.getSharedPreferences(HealthWorkoutBridgeReceiver.PREFS, 0)
    }
    var saveToHealthConnect by remember {
        mutableStateOf(preferences.getBoolean(HealthWorkoutBridgeReceiver.PREF_SAVE_HEALTH_CONNECT, false))
    }
    var stepGoal by remember {
        mutableIntStateOf(preferences.getInt(PREF_DAILY_STEP_GOAL, DEFAULT_DAILY_STEP_GOAL))
    }
    var selectedTypeKey by rememberSaveable { mutableStateOf(WorkoutType.WALKING.key) }
    var selectedTab by rememberSaveable { mutableStateOf(HealthTab.TODAY) }
    var selectedWorkoutStart by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingLocationWorkout by remember { mutableStateOf<WorkoutType?>(null) }
    val active = activeHealthWorkout != null || workoutState.status == "WAITING" || workoutState.status == "ACTIVE"
    val isConnected = workoutState.status != "DISCONNECTED"
    val currentBpm = workoutState.bpm?.takeIf {
        workoutState.capturedAtEpochMillis?.let { captured -> now - captured < SAMPLE_FRESHNESS_MS } == true
    }
    val startedAt = activeHealthWorkout?.startedAtEpochMillis ?: workoutState.startedAtEpochMillis
    val paused = activeHealthWorkout?.pausedAtEpochMillis != null
    val elapsed = activeHealthWorkout?.let { HealthWorkoutSessionStore.elapsedMillis(it, now) }
        ?: startedAt?.let { (now - it).coerceAtLeast(0L) }
        ?: 0L
    val healthConnectStatus = remember(context) { HealthConnectClient.getSdkStatus(context) }
    val healthPermissions = remember {
        setOf(
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    }
    val healthPermissionsToRequest = remember(healthPermissions) {
        healthPermissions +
            HealthPermission.getWritePermission(DistanceRecord::class) +
            HealthPermission.getWritePermission(androidx.health.connect.client.records.SpeedRecord::class) +
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE
    }
    val readHealthPermissions = HealthConnectTodayReader.readPermissions
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val enabled = granted.containsAll(healthPermissions)
        saveToHealthConnect = enabled
        preferences.edit().putBoolean(HealthWorkoutBridgeReceiver.PREF_SAVE_HEALTH_CONNECT, enabled).apply()
        if (!enabled) CompanionWorkoutStateHolder.reportHealthSaved(
            false,
            if (isKorean) "Health Connect 저장 권한이 허용되지 않았습니다." else "Health Connect access was not granted.",
        )
        scope.launch { HealthConnectTodayReader.refresh(context) }
    }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        scope.launch { HealthConnectTodayReader.refresh(context) }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val type = pendingLocationWorkout
        pendingLocationWorkout = null
        if (type != null) {
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            startWorkout(context, type, withGps = granted)
        }
    }
    val startSelectedWorkout = {
        val type = WorkoutType.fromKey(selectedTypeKey)
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (type.recordsGps && !hasLocation) {
            pendingLocationWorkout = type
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            startWorkout(context, type, withGps = type.recordsGps && hasLocation)
        }
    }
    val pauseSelectedWorkout = { pauseWorkout(context) }
    val resumeSelectedWorkout = { resumeWorkout(context) }

    LaunchedEffect(healthOverview.permissionCheckComplete, healthOverview.writePermissionsGranted) {
        if (healthOverview.permissionCheckComplete && saveToHealthConnect && !healthOverview.writePermissionsGranted) {
            saveToHealthConnect = false
            preferences.edit().putBoolean(HealthWorkoutBridgeReceiver.PREF_SAVE_HEALTH_CONNECT, false).apply()
        }
    }

    LaunchedEffect(active, startedAt, paused) {
        while (active && !paused) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    val selectedType = WorkoutType.fromKey(selectedTypeKey)
    val currentWorkoutType = activeHealthWorkout?.let { WorkoutType.fromKey(it.workoutType) } ?: selectedType
    val liquidBackdrop = rememberLayerBackdrop()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (useMaterial) WindowInsets.navigationBars else WindowInsets.statusBars,
        bottomBar = {
            if (useMaterial) {
                HealthBottomNavigation(
                    selected = selectedTab,
                    useMaterial = true,
                    isKorean = isKorean,
                    onSelected = { selectedTab = it },
                )
            } else {
                IosFitnessBottomNavigation(
                    selected = selectedTab.name,
                    isKorean = isKorean,
                    backdrop = liquidBackdrop,
                    onSelected = { selectedTab = HealthTab.valueOf(it) },
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 600.dp
            if (!useMaterial) {
                Box(
                    Modifier.matchParentSize()
                        .background(MaterialTheme.colorScheme.background)
                        .layerBackdrop(liquidBackdrop),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = 960.dp),
            ) {
                if (useMaterial) {
                    HealthHeader(
                        tab = selectedTab,
                        useMaterial = true,
                        isKorean = isKorean,
                        onHistoryBack = { selectedWorkoutStart = 0L },
                        showBack = selectedTab == HealthTab.HISTORY && selectedWorkoutStart > 0L,
                    )
                } else {
                    IosFitnessHeader(
                        title = when (selectedTab) {
                            HealthTab.TODAY -> if (isKorean) "요약" else "Summary"
                            HealthTab.WORKOUT -> if (isKorean) "운동" else "Workout"
                            HealthTab.HISTORY -> if (selectedWorkoutStart > 0L) {
                                if (isKorean) "운동 세부사항" else "Workout details"
                            } else if (isKorean) "기록" else "History"
                        },
                        subtitle = if (selectedTab == HealthTab.TODAY) fitnessCurrentDate(isKorean) else null,
                        showBack = selectedTab == HealthTab.HISTORY && selectedWorkoutStart > 0L,
                        onBack = { selectedWorkoutStart = 0L },
                    )
                }
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        fadeIn(tween(if (motionEnabled) 180 else 0)) togetherWith
                            fadeOut(tween(if (motionEnabled) 120 else 0))
                    },
                    label = "health_sections",
                ) { tab ->
                    when (tab) {
                        HealthTab.TODAY -> if (useMaterial) TodayScreen(
                            wide = wide,
                            isKorean = isKorean,
                            useMaterial = useMaterial,
                            healthOverview = healthOverview,
                            healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE,
                            onConnectHealthData = { readPermissionLauncher.launch(readHealthPermissions) },
                            stepGoal = stepGoal,
                            onStepGoalChanged = { goal ->
                                stepGoal = goal
                                preferences.edit().putInt(PREF_DAILY_STEP_GOAL, goal).apply()
                            },
                            echoPodsInstalled = echoPodsInstalled,
                            activeWorkoutType = currentWorkoutType,
                            isConnected = isConnected,
                            active = active,
                            paused = paused,
                            locationTracking = locationTracking,
                            currentBpm = currentBpm,
                            status = workoutState.status,
                        recentSamples = workoutState.recentSamples,
                            elapsed = elapsed,
                            averageBpm = workoutState.averageBpm,
                            maxBpm = workoutState.maxBpm,
                            sampleCount = workoutState.sampleCount,
                            history = history,
                            onTypeSelected = { selectedTypeKey = it.key },
                            onStart = startSelectedWorkout,
                            onPause = pauseSelectedWorkout,
                            onResume = resumeSelectedWorkout,
                            onStop = { finishWorkout(context, workoutState.recentSamples, saveToHealthConnect, scope) },
                            onOpenEchoPods = { openEchoPods(context) },
                            onOpenWorkoutTab = { selectedTab = HealthTab.WORKOUT },
                            onOpenHistory = { start ->
                                selectedWorkoutStart = start
                                selectedTab = HealthTab.HISTORY
                            },
                        ) else IosFitnessTodayScreen(
                            backdrop = liquidBackdrop,
                            wide = wide,
                            isKorean = isKorean,
                            overview = healthOverview,
                            healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE,
                            onConnectHealthData = { readPermissionLauncher.launch(readHealthPermissions) },
                            stepGoal = stepGoal,
                            onStepGoalChanged = { goal ->
                                stepGoal = goal
                                preferences.edit().putInt(PREF_DAILY_STEP_GOAL, goal).apply()
                            },
                            echoPodsInstalled = echoPodsInstalled,
                            selectedWorkoutType = currentWorkoutType,
                            isConnected = isConnected,
                            active = active,
                            paused = paused,
                            locationTracking = locationTracking,
                            currentBpm = currentBpm,
                            status = workoutState.status,
                            elapsed = elapsed,
                            averageBpm = workoutState.averageBpm,
                            maxBpm = workoutState.maxBpm,
                            sampleCount = workoutState.sampleCount,
                            recentSamples = workoutState.recentSamples,
                            history = history,
                            onWorkoutTypeSelected = { selectedTypeKey = it.key },
                            onStart = startSelectedWorkout,
                            onPause = pauseSelectedWorkout,
                            onResume = resumeSelectedWorkout,
                            onStop = { finishWorkout(context, workoutState.recentSamples, saveToHealthConnect, scope) },
                            onOpenEchoPods = { openEchoPods(context) },
                            onOpenWorkoutTab = { selectedTab = HealthTab.WORKOUT },
                            onOpenHistory = { start ->
                                selectedWorkoutStart = start
                                selectedTab = HealthTab.HISTORY
                            },
                        )

                        HealthTab.WORKOUT -> if (useMaterial) WorkoutScreen(
                            wide = wide,
                            isKorean = isKorean,
                            useMaterial = useMaterial,
                            echoPodsInstalled = echoPodsInstalled,
                            activeWorkoutType = currentWorkoutType,
                            isConnected = isConnected,
                            active = active,
                            paused = paused,
                            locationTracking = locationTracking,
                            currentBpm = currentBpm,
                            status = workoutState.status,
                            recentSamples = workoutState.recentSamples,
                            elapsed = elapsed,
                            averageBpm = workoutState.averageBpm,
                            maxBpm = workoutState.maxBpm,
                            sampleCount = workoutState.sampleCount,
                            onTypeSelected = { selectedTypeKey = it.key },
                            onStart = startSelectedWorkout,
                            onPause = pauseSelectedWorkout,
                            onResume = resumeSelectedWorkout,
                            onStop = { finishWorkout(context, workoutState.recentSamples, saveToHealthConnect, scope) },
                            onOpenEchoPods = { openEchoPods(context) },
                        ) else IosFitnessWorkoutScreen(
                            backdrop = liquidBackdrop,
                            isKorean = isKorean,
                            echoPodsInstalled = echoPodsInstalled,
                            selectedWorkoutType = currentWorkoutType,
                            isConnected = isConnected,
                            active = active,
                            paused = paused,
                            locationTracking = locationTracking,
                            currentBpm = currentBpm,
                            status = workoutState.status,
                            elapsed = elapsed,
                            averageBpm = workoutState.averageBpm,
                            maxBpm = workoutState.maxBpm,
                            sampleCount = workoutState.sampleCount,
                            recentSamples = workoutState.recentSamples,
                            onWorkoutTypeSelected = { selectedTypeKey = it.key },
                            onStart = startSelectedWorkout,
                            onPause = pauseSelectedWorkout,
                            onResume = resumeSelectedWorkout,
                            onStop = { finishWorkout(context, workoutState.recentSamples, saveToHealthConnect, scope) },
                            onOpenEchoPods = { openEchoPods(context) },
                        )

                        HealthTab.HISTORY -> HistoryScreen(
                            wide = wide,
                            isKorean = isKorean,
                            useMaterial = useMaterial,
                            history = history,
                            selectedWorkoutStart = selectedWorkoutStart,
                            saveToHealthConnect = saveToHealthConnect,
                            healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE,
                            healthRecordSaved = workoutState.healthRecordSaved,
                            healthMessage = workoutState.message,
                            syncedSessions = healthOverview.recentExerciseSessions,
                            onWorkoutSelected = { selectedWorkoutStart = it },
                            onSaveToHealthConnectChanged = { enabled ->
                                if (!enabled) {
                                    saveToHealthConnect = false
                                    preferences.edit().putBoolean(
                                        HealthWorkoutBridgeReceiver.PREF_SAVE_HEALTH_CONNECT,
                                        false,
                                    ).apply()
                                } else {
                                    permissionLauncher.launch(healthPermissionsToRequest)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthHeader(
    tab: HealthTab,
    useMaterial: Boolean,
    isKorean: Boolean,
    showBack: Boolean,
    onHistoryBack: () -> Unit,
) {
    val title = when (tab) {
        HealthTab.TODAY -> if (isKorean) "오늘" else "Today"
        HealthTab.WORKOUT -> if (isKorean) "운동" else "Workout"
        HealthTab.HISTORY -> if (isKorean) "기록" else "History"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onHistoryBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (isKorean) "뒤로" else "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "ECHO HEALTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    title,
                    style = if (useMaterial) MaterialTheme.typography.headlineLarge
                    else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (tab == HealthTab.TODAY) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        if (isKorean) "Pro 3 심박 · 실험" else "Pro 3 HR · experimental",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    wide: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    healthOverview: HealthConnectTodayState,
    healthConnectAvailable: Boolean,
    onConnectHealthData: () -> Unit,
    stepGoal: Int,
    onStepGoalChanged: (Int) -> Unit,
    echoPodsInstalled: Boolean,
    activeWorkoutType: WorkoutType,
    isConnected: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    currentBpm: Int?,
    status: String,
    recentSamples: List<CompanionHeartRateSample>,
    elapsed: Long,
    averageBpm: Int?,
    maxBpm: Int?,
    sampleCount: Int,
    history: List<SavedWorkout>,
    onTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
    onOpenWorkoutTab: () -> Unit,
    onOpenHistory: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ActivitySummaryCard(
            overview = healthOverview,
            available = healthConnectAvailable,
            isKorean = isKorean,
            useMaterial = useMaterial,
            stepGoal = stepGoal,
            onStepGoalChanged = onStepGoalChanged,
            onConnect = onConnectHealthData,
        )
        healthOverview.latestSleep?.let { FitnessSleepStagesCard(it, isKorean, useMaterial) }
        Spacer(Modifier.height(14.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeartRateCard(
                        currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm, sampleCount, recentSamples,
                        isKorean, useMaterial,
                    )
                    WorkoutStartCard(
                        activeWorkoutType, isKorean, useMaterial, isConnected, active,
                        paused, locationTracking, elapsed, echoPodsInstalled, onTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TodayHighlightsCard(history, isKorean, useMaterial, onOpenHistory)
                    RecentWorkoutsCard(history.take(3), isKorean, useMaterial, onOpenHistory)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeartRateCard(
                    currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm, sampleCount, recentSamples,
                    isKorean, useMaterial,
                )
                WorkoutStartCard(
                    activeWorkoutType, isKorean, useMaterial, isConnected, active,
                    paused, locationTracking, elapsed, echoPodsInstalled, onTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods,
                )
                TodayHighlightsCard(history, isKorean, useMaterial, onOpenHistory)
                RecentWorkoutsCard(history.take(3), isKorean, useMaterial, onOpenHistory)
                TextButton(onClick = onOpenWorkoutTab, modifier = Modifier.align(Alignment.End)) {
                    Text(if (isKorean) "운동 화면 열기" else "Open workout")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActivitySummaryCard(
    overview: HealthConnectTodayState,
    available: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    stepGoal: Int,
    onStepGoalChanged: (Int) -> Unit,
    onConnect: () -> Unit,
) {
    val stepNumber = remember(overview.todaySteps, isKorean) {
        overview.todaySteps?.let { NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault()).format(it) }
    }
    val sleepLabel = overview.recentSleepSeconds?.let { seconds ->
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        if (isKorean) {
            if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
        } else {
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    }
    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "활동 요약" else "Activity summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isKorean) "Health Connect에 동기화된 기록" else "Synced through Health Connect",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!overview.stepsPermissionGranted || !overview.sleepPermissionGranted ||
                !overview.activeCaloriesPermissionGranted || !overview.distancePermissionGranted ||
                !overview.exercisePermissionGranted
            ) {
                TextButton(onClick = onConnect, enabled = available) {
                    Text(if (isKorean) "권한 허용" else "Allow access")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "오늘 걸음" else "Steps today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stepNumber ?: "—",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !available -> if (isKorean) "Health Connect를 사용할 수 없음" else "Health Connect unavailable"
                        !overview.stepsPermissionGranted -> if (isKorean) "걸음 수 읽기 권한 필요" else "Allow step access"
                        overview.todaySteps == null -> if (isKorean) "아직 동기화된 기록 없음" else "No synced records yet"
                        else -> if (isKorean) "오늘 합계" else "Today's total"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (overview.todaySteps != null) {
                    Spacer(Modifier.height(9.dp))
                    StepGoalBar(progress = (overview.todaySteps / stepGoal.toFloat()).coerceIn(0f, 1f))
                }
                StepGoalPicker(stepGoal, isKorean, useMaterial, onStepGoalChanged)
            }
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "수면 · 최근 24시간" else "Sleep · past 24 hours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    sleepLabel ?: "—",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !available -> if (isKorean) "Health Connect를 사용할 수 없음" else "Health Connect unavailable"
                        !overview.sleepPermissionGranted -> if (isKorean) "수면 기록 읽기 권한 필요" else "Allow sleep access"
                        overview.recentSleepSeconds == null -> if (isKorean) "아직 동기화된 기록 없음" else "No synced records yet"
                        else -> if (isKorean) "동기화된 수면 기록" else "Synced sleep record"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "오늘 이동 거리" else "Distance today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    overview.todayDistanceMeters?.let { formatWorkoutDistance(it, isKorean) } ?: "—",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(if (overview.distancePermissionGranted) if (isKorean) "Health Connect 동기화" else "Synced through Health Connect" else if (isKorean) "거리 읽기 권한 필요" else "Allow distance access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "활동 에너지" else "Active energy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    overview.todayActiveCaloriesKcal?.let { "${NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault()).format(it.roundToInt())} kcal" } ?: "—",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(if (overview.activeCaloriesPermissionGranted) if (isKorean) "Health Connect 동기화" else "Synced through Health Connect" else if (isKorean) "활동 에너지 권한 필요" else "Allow energy access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FitnessHourlyStepsChart(overview.hourlySteps, isKorean)
        FitnessWeeklyStepsChart(overview.weekSteps, isKorean)
        if (!overview.stepsPermissionGranted || !overview.sleepPermissionGranted ||
            !overview.activeCaloriesPermissionGranted || !overview.distancePermissionGranted ||
            !overview.exercisePermissionGranted
        ) {
            Text(
                if (isKorean) "에코팟과 에코 헬스는 같은 서명으로 설치되면 자동 연동됩니다. 걸음·수면·거리·운동 기록은 Health Connect 권한과 제공 앱의 공유 설정이 필요합니다." else "EchoPods and Echo Health link automatically when installed with the same signing key. Health Connect access and source-app sharing are required for steps, sleep, distance, and workouts.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (overview.readFailed) {
            Text(
                if (isKorean) "건강 기록을 읽지 못했습니다. 권한과 Health Connect 동기화를 확인한 뒤 다시 열어주세요." else "Health records could not be read. Check permissions and Health Connect sync, then reopen this screen.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun StepGoalPicker(goal: Int, isKorean: Boolean, useMaterial: Boolean, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
            val formatted = NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault()).format(goal)
            Text(if (isKorean) "목표 ${formatted}걸음 · 변경" else "${formatted}-step goal · Change", style = MaterialTheme.typography.labelSmall)
        }
        if (useMaterial) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                stepGoalOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (isKorean) "${NumberFormat.getIntegerInstance(Locale.KOREA).format(option)}걸음" else NumberFormat.getIntegerInstance().format(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        } else if (expanded) {
            Dialog(onDismissRequest = { expanded = false }) {
                Surface(
                    modifier = Modifier.widthIn(max = 380.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    shadowElevation = 20.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isKorean) "일일 걸음 목표" else "Daily step goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (isKorean) "하루 목표를 선택하세요" else "Choose a daily target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        stepGoalOptions.forEach { option ->
                            val selected = option == goal
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                                    .clickable {
                                        onSelected(option)
                                        expanded = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val label = NumberFormat.getIntegerInstance(if (isKorean) Locale.KOREA else Locale.getDefault()).format(option)
                                Text(if (isKorean) "${label}걸음" else label, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val stepGoalOptions = listOf(4_000, 6_000, 8_000, 10_000, 12_000, 15_000)

@Composable
internal fun FitnessHourlyStepsChart(data: List<HourlyStepCount>, isKorean: Boolean) {
    if (data.isEmpty()) return
    val visible = data.takeLast(12)
    val maxSteps = visible.mapNotNull { it.total }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val active = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(if (isKorean) "오늘 시간대별 걸음" else "Steps by hour today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            visible.forEachIndexed { index, bucket ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                        val barWidth = size.width * 0.62f
                        val left = (size.width - barWidth) / 2f
                        drawRoundRect(track, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
                        val barHeight = bucket.total?.takeIf { it > 0L }?.let { (size.height * (it.toFloat() / maxSteps).coerceIn(0.04f, 1f)) } ?: 0f
                        if (barHeight > 0f) drawRoundRect(active, topLeft = Offset(left, size.height - barHeight), size = Size(barWidth, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
                    }
                    if (index == 0 || index % 3 == 0 || index == visible.lastIndex) {
                        Text(if (isKorean) "${bucket.hour}시" else bucket.hour.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    } else {
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepGoalBar(progress: Float) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val radius = size.height / 2f
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        if (progress > 0f) {
            drawRoundRect(
                fill,
                size = androidx.compose.ui.geometry.Size(size.width * progress, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        }
    }
}

@Composable
internal fun HeartRateCard(
    currentBpm: Int?,
    status: String,
    active: Boolean,
    echoPodsInstalled: Boolean,
    elapsed: Long,
    average: Int?,
    max: Int?,
    sampleCount: Int,
    recentSamples: List<CompanionHeartRateSample>,
    isKorean: Boolean,
    useMaterial: Boolean,
) {
    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "심박수" else "Heart rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        status == "ACTIVE" -> if (isKorean) "실시간 측정" else "Live"
                        status == "WAITING" -> if (isKorean) "센서 응답 대기 중" else "Waiting for sensor"
                        status == "NO_DATA" -> if (isKorean) "측정값을 받지 못함" else "No sensor data"
                        status == "DISCONNECTED" && echoPodsInstalled -> if (isKorean) "앱 자동 연동됨 · AirPods 연결 필요" else "Apps linked · connect AirPods"
                        status == "DISCONNECTED" -> if (isKorean) "에코팟 설치 필요" else "Install EchoPods"
                        else -> if (isKorean) "운동 중에만 측정" else "Measured during workouts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = currentBpm,
                transitionSpec = {
                    fadeIn(tween(if (ValueAnimator.areAnimatorsEnabled()) 200 else 0)) togetherWith
                        fadeOut(tween(if (ValueAnimator.areAnimatorsEnabled()) 140 else 0))
                },
                label = "live_heart_rate",
            ) { value ->
                Text(
                    value?.toString() ?: "—",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                " bpm",
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (active) Text(
                formatDuration(elapsed),
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MetricValue(if (isKorean) "평균" else "Average", average?.toString() ?: "—")
            MetricValue(if (isKorean) "최고" else "Maximum", max?.toString() ?: "—")
            MetricValue(if (isKorean) "샘플" else "Samples", sampleCount.toString())
        }
        if (recentSamples.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HeartRateChart(recentSamples, Modifier.fillMaxWidth().height(72.dp), isKorean)
        } else {
            Text(
                when {
                    active && status == "WAITING" -> if (isKorean) "AirPods에서 새 심박 샘플을 기다리고 있습니다." else "Waiting for a heart-rate sample from AirPods."
                    else -> if (isKorean) "실제 심박 샘플이 들어오면 추세를 표시합니다." else "A trend appears when real heart-rate samples arrive."
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WorkoutProgressCard(
    tracking: WorkoutTrackingData,
    type: WorkoutType,
    elapsedMillis: Long,
    paused: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    backdrop: LayerBackdrop? = null,
) {
    val context = LocalContext.current
    val distanceLabel = formatWorkoutDistance(tracking.distanceMeters, isKorean)
    val liveFixFresh = tracking.lastLocationAtEpochMillis?.let { capturedAt ->
        (System.currentTimeMillis() - capturedAt) in 0..LIVE_SPEED_FRESHNESS_MS
    } == true
    val liveSpeed = tracking.currentSpeedMetersPerSecond?.takeIf { liveFixFresh && it > 0.2 }
    val averageSpeed = (tracking.distanceMeters / (elapsedMillis / 1_000.0).coerceAtLeast(1.0)).takeIf { it > 0.2 }
    val paceLabel = when {
        !type.recordsGps || tracking.distanceMeters < 10.0 -> "—"
        type == WorkoutType.CYCLING -> liveSpeed?.let { speed ->
            "%.1f %s".format(speed * 3.6, if (isKorean) "km/h" else "km/h")
        } ?: averageSpeed?.let { "%.1f km/h".format(it * 3.6) } ?: "—"
        else -> {
            val metersPerSecond = liveSpeed ?: averageSpeed
            metersPerSecond?.let { speed -> formatPacePerKilometer(1_000.0 / speed, isKorean) } ?: "—"
        }
    }
    HealthCard(useMaterial) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutMetric(Modifier.weight(1f), if (isKorean) "시간" else "Duration", formatDuration(elapsedMillis))
            WorkoutMetric(Modifier.weight(1f), if (isKorean) "거리" else "Distance", distanceLabel)
            WorkoutMetric(Modifier.weight(1f), if (type == WorkoutType.CYCLING) (if (isKorean) "속도" else "Speed") else (if (isKorean) "페이스" else "Pace"), paceLabel)
        }
        tracking.laps.lastOrNull()?.let { lap ->
            Text(
                if (isKorean) "최근 구간 · ${formatDuration(lap.activeDurationMillis)}${lap.distanceMeters.takeIf { it > 0.0 }?.let { " · ${formatWorkoutDistance(it, true)}" } ?: ""} · ${tracking.laps.size}개 기록"
                else "Last lap · ${formatDuration(lap.activeDurationMillis)}${lap.distanceMeters.takeIf { it > 0.0 }?.let { " · ${formatWorkoutDistance(it, false)}" } ?: ""} · ${tracking.laps.size} saved",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!paused) {
            Spacer(Modifier.height(8.dp))
            if (!useMaterial && backdrop != null) {
                HealthIosGlassControl(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    onClick = { WorkoutLocationTracker.recordLap(context, elapsedMillis) },
                ) {
                    Text(if (isKorean) "구간 기록" else "Record lap", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(
                    onClick = { WorkoutLocationTracker.recordLap(context, elapsedMillis) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(if (isKorean) "구간 기록" else "Record lap") }
            }
        }
        if (type.recordsGps) {
            Spacer(Modifier.height(10.dp))
            when {
                paused -> Text(if (isKorean) "일시정지 중 · 위치 기록도 멈췄습니다" else "Paused · location recording is stopped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                tracking.locationStatus == "RECORDING" -> Text(
                    if (isKorean) "GPS 기록 중${tracking.locationAccuracyMeters?.let { " · 정확도 약 ${it.roundToInt()}m" } ?: ""}" else "Recording GPS${tracking.locationAccuracyMeters?.let { " · accuracy ~${it.roundToInt()}m" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tracking.locationStatus == "UNAVAILABLE" -> Text(if (isKorean) "위치 신호를 사용할 수 없습니다. 시간과 심박 기록은 계속됩니다." else "Location is unavailable. Time and heart-rate recording continue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                tracking.locationStatus == "NOT_RECORDED" -> Text(if (isKorean) "위치 권한을 허용하지 않아 경로를 기록하지 않습니다." else "Route is not recorded without location permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(if (isKorean) "GPS 신호를 찾는 중…" else "Waiting for GPS…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WorkoutRoutePreview(tracking.route, Modifier.padding(top = 6.dp))
        } else {
            Text(
                if (isKorean) "실내 운동 · 위치 정보는 수집하지 않습니다" else "Indoor workout · location is not collected",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutMetric(modifier: Modifier, label: String, value: String) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun formatWorkoutDistance(meters: Double, isKorean: Boolean): String = when {
    meters >= 1_000.0 -> "%.2f km".format(meters / 1_000.0)
    meters > 0.0 -> "%.0f m".format(meters)
    else -> "—"
}

private fun formatPacePerKilometer(secondsPerKm: Double, isKorean: Boolean): String {
    if (!secondsPerKm.isFinite() || secondsPerKm <= 0.0) return "—"
    val total = secondsPerKm.toInt()
    val minutes = total / 60
    val seconds = total % 60
    return if (isKorean) "%d:%02d /km".format(minutes, seconds) else "%d:%02d /km".format(minutes, seconds)
}

private const val LIVE_SPEED_FRESHNESS_MS = 6_000L

private fun shareWorkout(context: Context, workout: SavedWorkout, isKorean: Boolean) {
    val type = WorkoutType.fromKey(workout.type).label(isKorean)
    val duration = formatDuration(workout.activeDurationMillis ?: (workout.endedAtEpochMillis - workout.startedAtEpochMillis))
    val gpx = workout.route.takeIf { it.size >= 2 }?.let { points ->
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"Echo Health\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>")
            append(type.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
            append("</name><trkseg>")
            points.forEach { point ->
                append("<trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">")
                point.altitudeMeters?.let { append("<ele>").append(it).append("</ele>") }
                append("<time>").append(java.time.Instant.ofEpochMilli(point.capturedAtEpochMillis)).append("</time></trkpt>")
            }
            append("</trkseg></trk></gpx>")
        }
    }
    val text = buildString {
        append(type).append(" · ").append(duration)
        workout.distanceMeters?.let { append(" · ").append(formatWorkoutDistance(it, isKorean)) }
        workout.averageBpm?.let { append(" · ").append(it).append(" bpm") }
    }
    val intent = if (gpx != null) {
        val exportDirectory = File(context.cacheDir, "workout_exports").apply { mkdirs() }
        val exportFile = File(exportDirectory, "echohealth_${workout.startedAtEpochMillis}.gpx")
        exportFile.writeText(gpx, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", exportFile)
        Intent(Intent.ACTION_SEND).apply {
            setType("application/gpx+xml")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            setClipData(ClipData.newUri(context.contentResolver, exportFile.name, uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else Intent(Intent.ACTION_SEND).apply {
        setType("text/plain")
        putExtra(Intent.EXTRA_SUBJECT, if (isKorean) "에코 헬스 운동 기록" else "Echo Health workout")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    intent.putExtra(Intent.EXTRA_SUBJECT, if (isKorean) "에코 헬스 운동 기록" else "Echo Health workout")
    context.startActivity(Intent.createChooser(intent, if (isKorean) "운동 기록 공유" else "Share workout"))
}

@Composable
private fun WorkoutStartCard(
    selectedType: WorkoutType,
    isKorean: Boolean,
    useMaterial: Boolean,
    isConnected: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    elapsed: Long,
    echoPodsInstalled: Boolean,
    onTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
) {
    HealthCard(useMaterial) {
        Text(if (isKorean) "운동 시작" else "Start a workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (isKorean) {
                "운동 시간과 종류는 에코 헬스에서 기록합니다. 심박 측정은 실험 기능이며, AirPods Pro 3와 호환 펌웨어가 필요하고 기기별 확인이 필요합니다."
            } else {
                "Echo Health records the workout even without earbuds. Heart-rate capture is experimental and needs AirPods Pro 3, compatible firmware, and device testing."
            },
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        WorkoutTypeSelector(selectedType, isKorean, onTypeSelected, enabled = !active)
        Spacer(Modifier.height(12.dp))
        if (active) {
            WorkoutProgressCard(locationTracking, WorkoutType.fromKey(locationTracking.workoutType ?: selectedType.key), elapsed, paused, isKorean, useMaterial)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = if (paused) onResume else onPause,
                    shape = if (useMaterial) MaterialTheme.shapes.large else RoundedCornerShape(16.dp),
                ) {
                    Text(if (paused) (if (isKorean) "재개" else "Resume") else (if (isKorean) "일시정지" else "Pause"), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    shape = if (useMaterial) MaterialTheme.shapes.large else RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null)
                    Text(if (isKorean) "종료" else "Finish", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = onStart,
                shape = if (useMaterial) MaterialTheme.shapes.large else RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null)
                Text(
                    if (isKorean) "${selectedType.label(true)} 시작" else "Start ${selectedType.label(false).lowercase()}",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!isConnected && echoPodsInstalled) {
            TextButton(onClick = onOpenEchoPods, modifier = Modifier.align(Alignment.End)) {
                Text(if (isKorean) "에코팟 열기" else "Open EchoPods")
            }
        } else if (!isConnected) {
            Text(
                if (isKorean) "에코팟을 설치하면 두 앱은 자동 연동됩니다." else "Install EchoPods; the apps link automatically.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutTypeSelector(
    selected: WorkoutType,
    isKorean: Boolean,
    onSelected: (WorkoutType) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        FilterChip(
            selected = true,
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            label = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isKorean) "운동 종류" else "Workout type", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(selected.label(isKorean), fontWeight = FontWeight.SemiBold)
                    Text("  ▾", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 480.dp),
        ) {
            WorkoutType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label(isKorean), maxLines = 1) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                    leadingIcon = if (selected == type) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null,
                )
            }
        }
    }
}

@Composable
private fun TodayHighlightsCard(
    history: List<SavedWorkout>,
    isKorean: Boolean,
    useMaterial: Boolean,
    onOpenHistory: (Long) -> Unit,
) {
    val todayStart = remember { System.currentTimeMillis() - 24 * 60 * 60 * 1000L }
    val recent = history.filter { it.startedAtEpochMillis >= todayStart }
    HealthCard(useMaterial) {
        Text(if (isKorean) "오늘 활동" else "Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        if (recent.isEmpty()) {
            Text(
                if (isKorean) "운동을 마치면 기록과 심박 요약이 여기에 표시됩니다." else "Finish a workout to see its heart-rate summary here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recent.forEach { workout ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenHistory(workout.startedAtEpochMillis) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(WorkoutType.fromKey(workout.type).label(isKorean), fontWeight = FontWeight.SemiBold)
                        Text(formatDuration(workout.activeDurationMillis ?: (workout.endedAtEpochMillis - workout.startedAtEpochMillis)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(workout.averageBpm?.let { "$it bpm" } ?: "—", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecentWorkoutsCard(
    workouts: List<SavedWorkout>,
    isKorean: Boolean,
    useMaterial: Boolean,
    onOpenHistory: (Long) -> Unit,
) {
    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isKorean) "최근 운동" else "Recent workouts", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        if (workouts.isEmpty()) {
            Text(if (isKorean) "아직 운동 기록이 없습니다." else "No workouts recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            workouts.forEach { workout ->
                WorkoutHistoryRow(workout, isKorean, onOpenHistory)
            }
        }
    }
}

@Composable
private fun WorkoutScreen(
    wide: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    echoPodsInstalled: Boolean,
    activeWorkoutType: WorkoutType,
    isConnected: Boolean,
    active: Boolean,
    paused: Boolean,
    locationTracking: WorkoutTrackingData,
    currentBpm: Int?,
    status: String,
    elapsed: Long,
    averageBpm: Int?,
    maxBpm: Int?,
    sampleCount: Int,
    recentSamples: List<CompanionHeartRateSample>,
    onTypeSelected: (WorkoutType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenEchoPods: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WorkoutStartCard(activeWorkoutType, isKorean, useMaterial, isConnected, active, paused, locationTracking, elapsed, echoPodsInstalled, onTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods)
                    ConnectionStatusCard(isConnected, echoPodsInstalled, isKorean, useMaterial, onOpenEchoPods)
                }
                Column(Modifier.weight(1f)) {
                    HeartRateCard(currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm, sampleCount, recentSamples, isKorean, useMaterial)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WorkoutStartCard(activeWorkoutType, isKorean, useMaterial, isConnected, active, paused, locationTracking, elapsed, echoPodsInstalled, onTypeSelected, onStart, onPause, onResume, onStop, onOpenEchoPods)
                HeartRateCard(currentBpm, status, active, echoPodsInstalled, elapsed, averageBpm, maxBpm, sampleCount, recentSamples, isKorean, useMaterial)
                ConnectionStatusCard(isConnected, echoPodsInstalled, isKorean, useMaterial, onOpenEchoPods)
            }
        }
        Text(
            if (isKorean) "측정은 운동 중에만 요청됩니다. 연결, 펌웨어, Android Bluetooth 지원에 따라 심박수가 제공되지 않을 수 있습니다. 운동 참고용이며 의료 진단용이 아닙니다." else "Sensor requests run only during a workout. Heart-rate data may be unavailable depending on AirPods firmware and Android Bluetooth support. For fitness reference only, not for medical diagnosis.",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    echoPodsInstalled: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    onOpenEchoPods: () -> Unit,
) {
    HealthCard(useMaterial) {
        Text(if (isKorean) "AirPods 연결" else "AirPods connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            when {
                isConnected -> if (isKorean) "에코팟과 자동 연동되었고 AirPods가 연결되어 있습니다." else "Linked automatically; AirPods are connected through EchoPods."
                echoPodsInstalled -> if (isKorean) "앱끼리는 자동 연동되었습니다. 심박수를 받으려면 에어팟을 에코팟에서 연결하세요." else "The apps are linked automatically. Connect AirPods in EchoPods to receive heart-rate data."
                else -> if (isKorean) "에코팟을 설치하면 두 앱이 자동 연동됩니다." else "The apps will link automatically when EchoPods is installed."
            },
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isConnected && echoPodsInstalled) TextButton(onClick = onOpenEchoPods, modifier = Modifier.align(Alignment.End)) {
            Text(if (isKorean) "에코팟 열기" else "Open EchoPods")
        }
    }
}

@Composable
private fun HistoryScreen(
    wide: Boolean,
    isKorean: Boolean,
    useMaterial: Boolean,
    history: List<SavedWorkout>,
    selectedWorkoutStart: Long,
    saveToHealthConnect: Boolean,
    healthConnectAvailable: Boolean,
    healthRecordSaved: Boolean,
    healthMessage: String?,
    syncedSessions: List<SyncedExerciseSession>,
    onWorkoutSelected: (Long) -> Unit,
    onSaveToHealthConnectChanged: (Boolean) -> Unit,
) {
    val selectedWorkout = history.firstOrNull { it.startedAtEpochMillis == selectedWorkoutStart }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        if (selectedWorkout != null) {
            WorkoutDetailCard(selectedWorkout, isKorean, useMaterial)
        } else if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.2f)) {
                    WorkoutHistoryList(history, isKorean, useMaterial, onWorkoutSelected)
                }
                Column(Modifier.weight(0.8f)) {
                    HealthConnectCard(
                        saveToHealthConnect, healthConnectAvailable, healthRecordSaved, healthMessage,
                        isKorean, useMaterial, onSaveToHealthConnectChanged,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WorkoutHistoryList(history, isKorean, useMaterial, onWorkoutSelected)
                HealthConnectCard(
                    saveToHealthConnect, healthConnectAvailable, healthRecordSaved, healthMessage,
                    isKorean, useMaterial, onSaveToHealthConnectChanged,
                )
            }
        }
        HealthConnectSyncedWorkoutsCard(syncedSessions, isKorean, useMaterial)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HealthConnectSyncedWorkoutsCard(
    sessions: List<SyncedExerciseSession>,
    isKorean: Boolean,
    useMaterial: Boolean,
) {
    HealthCard(useMaterial) {
        Text(if (isKorean) "연결된 앱의 운동" else "Workouts from connected apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (isKorean) "최근 7일 · Health Connect에서 동기화" else "Last 7 days · synced through Health Connect",
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sessions.isEmpty()) {
            Text(
                if (isKorean) "공유된 운동 기록이 없습니다. Samsung Health 등에서 Health Connect 운동 공유를 켜면 여기에 표시됩니다." else "No shared workouts yet. Enable exercise sharing in Samsung Health or another source app to show its records here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.take(12).forEach { session ->
                val type = WorkoutType.entries.firstOrNull { it.healthConnectType == session.exerciseType }
                val label = session.title?.takeIf { it.isNotBlank() && !it.startsWith("EchoPods ·") }
                    ?: type?.label(isKorean)
                    ?: if (isKorean) "운동" else "Workout"
                val source = when {
                    session.originPackage.contains("shealth", ignoreCase = true) -> "Samsung Health"
                    session.originPackage.contains("fitness", ignoreCase = true) -> "Google Fit"
                    else -> "Health Connect"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatDate(session.startEpochMillis, isKorean)} · $source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatDuration(session.endEpochMillis - session.startEpochMillis), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryList(
    history: List<SavedWorkout>,
    isKorean: Boolean,
    useMaterial: Boolean,
    onWorkoutSelected: (Long) -> Unit,
) {
    HealthCard(useMaterial) {
        Text(if (isKorean) "운동 기록" else "Workout history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (history.isEmpty()) {
            Text(
                if (isKorean) "운동을 마치면 기록이 저장됩니다. 심박수는 이 기기에만 보관되며, Health Connect 저장을 별도로 켤 수 있습니다." else "Completed workouts are saved on this device. Health Connect sharing is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else history.forEach { workout -> WorkoutHistoryRow(workout, isKorean, onWorkoutSelected) }
    }
}

@Composable
internal fun WorkoutHistoryRow(
    workout: SavedWorkout,
    isKorean: Boolean,
    onClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(workout.startedAtEpochMillis) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(WorkoutType.fromKey(workout.type).label(isKorean), fontWeight = FontWeight.SemiBold)
            Text(formatDate(workout.startedAtEpochMillis, isKorean), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(workout.activeDurationMillis ?: (workout.endedAtEpochMillis - workout.startedAtEpochMillis)), fontWeight = FontWeight.SemiBold)
            Text(workout.distanceMeters?.let { formatWorkoutDistance(it, isKorean) } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(workout.averageBpm?.let { "$it bpm" } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WorkoutDetailCard(workout: SavedWorkout, isKorean: Boolean, useMaterial: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HealthCard(useMaterial) {
            Text(WorkoutType.fromKey(workout.type).label(isKorean), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(formatDate(workout.startedAtEpochMillis, isKorean), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValue(if (isKorean) "시간" else "Duration", formatDuration(workout.activeDurationMillis ?: (workout.endedAtEpochMillis - workout.startedAtEpochMillis)))
                MetricValue(if (isKorean) "거리" else "Distance", workout.distanceMeters?.let { formatWorkoutDistance(it, isKorean) } ?: "—")
                MetricValue(if (isKorean) "평균" else "Average", workout.averageBpm?.toString() ?: "—")
                MetricValue(if (isKorean) "최고" else "Maximum", workout.maxBpm?.toString() ?: "—")
            }
            if (workout.route.size >= 2) {
                Spacer(Modifier.height(14.dp))
                WorkoutRoutePreview(workout.route)
            }
            if (workout.laps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(if (isKorean) "구간" else "Laps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                workout.laps.forEachIndexed { index, lap ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("${index + 1}", modifier = Modifier.widthIn(min = 24.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(formatDuration(lap.activeDurationMillis), modifier = Modifier.weight(1f))
                        Text(lap.distanceMeters.takeIf { it > 0.0 }?.let { formatWorkoutDistance(it, isKorean) } ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(
                onClick = { shareWorkout(context, workout, isKorean) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (isKorean) "운동 기록 공유" else "Share workout")
            }
        }
        HealthCard(useMaterial) {
            Text(if (isKorean) "심박수 변화" else "Heart rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            HeartRateChart(workout.samples, Modifier.fillMaxWidth().height(190.dp), isKorean)
            if (workout.samples.isEmpty()) Text(
                if (isKorean) "이 운동에서는 심박수 샘플을 받지 못했습니다." else "No heart-rate samples were received for this workout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthConnectCard(
    enabled: Boolean,
    available: Boolean,
    saved: Boolean,
    message: String?,
    isKorean: Boolean,
    useMaterial: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    HealthCard(useMaterial) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isKorean) "Health Connect 저장" else "Save to Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (available) {
                        if (isKorean) "운동 시간과 종류를 저장하고, 심박 샘플을 받았다면 함께 씁니다. 걸음·수면 읽기 권한과는 별도입니다." else "Saves workout type and duration, plus heart-rate samples when available. This is separate from step and sleep access."
                    } else {
                        if (isKorean) "Health Connect를 사용할 수 없습니다." else "Health Connect is unavailable."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, enabled = available, onCheckedChange = onEnabledChanged)
        }
        if (enabled) {
            Text(
                if (isKorean) "Samsung Health에 표시하려면 Samsung Health 설정에서 Health Connect 동기화를 켜세요." else "To see these records in Samsung Health, enable Health Connect sync in Samsung Health settings.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (saved) Text(
            if (isKorean) "최근 운동을 Health Connect에 저장했습니다." else "Workout saved to Health Connect.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        message?.takeIf { it.contains("Health Connect", ignoreCase = true) }?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetricValue(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HealthCard(
    useMaterial: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(if (ValueAnimator.areAnimatorsEnabled()) 220 else 0))
            .then(if (useMaterial) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), shape)),
        shape = shape,
        color = if (useMaterial) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (useMaterial) 1.dp else 0.dp,
        shadowElevation = if (useMaterial) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (useMaterial) 18.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun HeartRateChart(
    samples: List<CompanionHeartRateSample>,
    modifier: Modifier = Modifier,
    isKorean: Boolean,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (isKorean) "운동 심박수 그래프" else "Workout heart-rate graph"
        },
    ) {
        val stroke = 2.dp.toPx()
        val padding = stroke * 2
        val y = size.height - padding
        drawLine(trackColor, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 1.dp.toPx())
        if (samples.isEmpty()) return@Canvas
        val valid = samples.takeLast(240)
        val minBpm = (valid.minOf { it.bpm } - 8).coerceAtLeast(35)
        val maxBpm = (valid.maxOf { it.bpm } + 8).coerceAtMost(220).coerceAtLeast(minBpm + 1)
        val path = Path()
        valid.forEachIndexed { index, sample ->
            val x = if (valid.size == 1) size.width / 2f else
                padding + (size.width - padding * 2) * index / (valid.lastIndex.toFloat())
            val normalized = (sample.bpm - minBpm).toFloat() / (maxBpm - minBpm)
            val pointY = size.height - padding - normalized * (size.height - padding * 2)
            if (index == 0) path.moveTo(x, pointY) else path.lineTo(x, pointY)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val last = valid.last()
        val x = if (valid.size == 1) size.width / 2f else size.width - padding
        val normalized = (last.bpm - minBpm).toFloat() / (maxBpm - minBpm)
        val pointY = size.height - padding - normalized * (size.height - padding * 2)
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, pointY))
    }
}

@Composable
private fun HealthBottomNavigation(
    selected: HealthTab,
    useMaterial: Boolean,
    isKorean: Boolean,
    onSelected: (HealthTab) -> Unit,
) {
    val items = listOf(
        Triple(HealthTab.TODAY, Icons.Outlined.Home, if (isKorean) "오늘" else "Today"),
        Triple(HealthTab.WORKOUT, Icons.AutoMirrored.Outlined.DirectionsRun, if (isKorean) "운동" else "Workout"),
        Triple(HealthTab.HISTORY, Icons.Outlined.History, if (isKorean) "기록" else "History"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(if (useMaterial) 24.dp else 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (useMaterial) Modifier else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    RoundedCornerShape(28.dp),
                ),
            )
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (tab, icon, label) ->
            val isSelected = selected == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (useMaterial) 20.dp else 23.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (useMaterial) 0.16f else 0.11f) else Color.Transparent)
                    .clickable { onSelected(tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun sendBridgeRequest(context: android.content.Context, action: String, workoutType: WorkoutType? = null) {
    val intent = Intent(action).setPackage(HealthWorkoutBridgeReceiver.ECHOPODS_PACKAGE)
    workoutType?.let { intent.putExtra(HealthWorkoutBridgeReceiver.EXTRA_WORKOUT_TYPE, it.key) }
    context.sendBroadcast(intent, HealthWorkoutBridgeReceiver.CONTROL_PERMISSION)
}

private fun startWorkout(context: android.content.Context, type: WorkoutType, withGps: Boolean) {
    val startedAt = System.currentTimeMillis()
    if (HealthWorkoutSessionStore.start(context, type.key, startedAt)) {
        WorkoutLocationTracker.start(context, type, withGps, startedAt)
        sendBridgeRequest(context, HealthWorkoutBridgeReceiver.ACTION_START_WORKOUT, type)
    }
}

private fun pauseWorkout(context: android.content.Context) {
    if (HealthWorkoutSessionStore.pause(context)) WorkoutLocationTracker.pause(context)
}

private fun resumeWorkout(context: android.content.Context) {
    if (HealthWorkoutSessionStore.resume(context)) WorkoutLocationTracker.resume(context)
}

private fun finishWorkout(
    context: android.content.Context,
    recentSamples: List<CompanionHeartRateSample>,
    saveToHealthConnect: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val active = HealthWorkoutSessionStore.session.value
    val activeElapsed = active?.let { HealthWorkoutSessionStore.elapsedMillis(it) } ?: 0L
    val tracking = WorkoutLocationTracker.finish(context, activeElapsed)
    val summary = HealthWorkoutSessionStore.finish(
        context = context,
        samples = recentSamples,
        distanceMeters = tracking.distanceMeters,
        route = tracking.route,
        laps = tracking.laps,
    )
    if (summary != null) {
        WorkoutHistoryStore.append(context, summary)
        if (saveToHealthConnect && CompanionWorkoutStateHolder.beginHealthWrite()) {
            scope.launch {
                try {
                    HealthConnectWorkoutWriter(context).write(summary)
                    CompanionWorkoutStateHolder.reportHealthSaved(true)
                } catch (_: Exception) {
                    CompanionWorkoutStateHolder.reportHealthSaved(
                        false,
                        "Health Connect save failed. Check its permissions and try again.",
                    )
                } finally {
                    CompanionWorkoutStateHolder.finishHealthWrite()
                }
            }
        }
    }
    sendBridgeRequest(context, HealthWorkoutBridgeReceiver.ACTION_STOP_WORKOUT)
}

private fun openEchoPods(context: android.content.Context) {
    context.packageManager.getLaunchIntentForPackage(HealthWorkoutBridgeReceiver.ECHOPODS_PACKAGE)?.let(context::startActivity)
}

private fun formatDuration(durationMillis: Long): String {
    val total = (durationMillis / 1_000).coerceAtLeast(0L)
    val hours = total / 3_600
    val minutes = (total % 3_600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatDate(epochMillis: Long, isKorean: Boolean): String {
    val pattern = if (isKorean) "M월 d일 · HH:mm" else "MMM d · HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
}

private const val SAMPLE_FRESHNESS_MS = 15_000L
