/* SPDX-License-Identifier: GPL-3.0-or-later */
package me.kavishdevar.librepods.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class HeartRateStreamStatus { DISCONNECTED, OFF, WAITING, ACTIVE, NO_DATA }

data class HeartRateStreamState(
    val status: HeartRateStreamStatus = HeartRateStreamStatus.DISCONNECTED,
    val bpm: Int? = null,
    val sampledAtEpochMillis: Long? = null,
    val workoutStartedAtEpochMillis: Long? = null,
    val sampleCount: Int = 0,
    val averageBpm: Int? = null,
    val maxBpm: Int? = null,
    val recentSamples: List<HeartRateWorkoutSample> = emptyList(),
)

data class HeartRateWorkoutSample(val bpm: Int, val capturedAtEpochMillis: Long)

data class HeartRateWorkoutSummary(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val samples: List<HeartRateWorkoutSample>,
    val workoutType: String = "OTHER",
) {
    val averageBpm: Int? get() = samples.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
    val maxBpm: Int? get() = samples.maxOfOrNull { it.bpm }
}

/** Bounded, in-memory recording for one user-started workout. */
internal class HeartRateWorkoutRecorder {
    private var startedAtEpochMillis: Long? = null
    private var workoutType: String = "OTHER"
    private val samples = ArrayList<HeartRateWorkoutSample>()

    @Synchronized
    fun start(nowEpochMillis: Long, workoutType: String) {
        samples.clear()
        startedAtEpochMillis = nowEpochMillis
        this.workoutType = workoutType.takeIf { it in SUPPORTED_WORKOUT_TYPES } ?: "OTHER"
    }

    @Synchronized
    fun add(sample: HeartRateWorkoutSample) {
        if (startedAtEpochMillis == null || samples.size >= MAX_SAMPLES) return
        if (sample.bpm !in 30..220) return
        val previous = samples.lastOrNull()?.capturedAtEpochMillis ?: startedAtEpochMillis!!
        if (sample.capturedAtEpochMillis > previous) samples += sample
    }

    @Synchronized
    fun sampleCount() = samples.size

    @Synchronized
    fun averageBpm(): Int? = samples.takeIf { it.isNotEmpty() }?.sumOf { it.bpm }?.div(samples.size)

    @Synchronized
    fun maxBpm(): Int? = samples.maxOfOrNull { it.bpm }

    @Synchronized
    fun recentSamples(): List<HeartRateWorkoutSample> = samples.takeLast(MAX_RECENT_SAMPLES)

    @Synchronized
    fun finish(endedAtEpochMillis: Long): HeartRateWorkoutSummary? {
        val start = startedAtEpochMillis ?: return null
        startedAtEpochMillis = null
        return HeartRateWorkoutSummary(
            startedAtEpochMillis = start,
            endedAtEpochMillis = maxOf(start + 1, endedAtEpochMillis),
            samples = samples.toList(),
            workoutType = workoutType,
        ).also { samples.clear() }
    }

    companion object {
        private const val MAX_SAMPLES = 8 * 60 * 60
        private const val MAX_RECENT_SAMPLES = 120
        private val SUPPORTED_WORKOUT_TYPES = setOf("WALKING", "RUNNING", "CYCLING", "HIKING", "OTHER")
    }
}

/** One isolated heart-rate stream bound to the AACP socket that created it. */
internal class HeartRateSession(
    private val socket: BluetoothSocket,
    private val sendPacket: (ByteArray) -> Boolean,
    private val state: MutableStateFlow<HeartRateStreamState>,
    private val pauseHeadTracking: () -> Boolean = { false },
    private val resumeHeadTracking: () -> Unit = {},
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reports = HeartRateSensorReports()
    private val workoutRecorder = HeartRateWorkoutRecorder()
    private val sequence = AtomicInteger(0)
    private var descriptorRequestAttempts = 0
    private var descriptorRequestJob: Job? = null
    private var activeHeartRateServices = emptyList<Int>()
    private var wanted = false
    private var closed = false
    private var lastSampleElapsed = 0L
    private var job: Job? = null
    private var headTrackingPauseAttempted = false
    private var resumeHeadTrackingAfterWorkout = false
    private var headTrackingResumeJob: Job? = null
    private var epoch = 0L

    private fun isCurrent() = !closed && socket.isConnected && BluetoothConnectionManager.aacpSocket === socket

    @Synchronized
    fun requestDescriptors() {
        if (closed || !isCurrent() || reports.heartRateService != null || descriptorRequestJob?.isActive == true) return
        requestDescriptorOnce()
        descriptorRequestJob = scope.launch {
            repeat(DESCRIPTOR_RETRIES) {
                delay(DESCRIPTOR_RETRY_DELAY_MS)
                val keepRetrying = synchronized(this@HeartRateSession) {
                    if (closed || !isCurrent() || reports.heartRateService != null) false
                    else {
                        requestDescriptorOnce()
                        true
                    }
                }
                if (!keepRetrying) return@launch
            }
        }
    }

    /** Repeat metadata discovery because some firmware ignores an early request during AACP init. */
    private fun requestDescriptorOnce(): Boolean {
        descriptorRequestAttempts++
        val sent = sendRtBuddy(SensorDataWxCodec.requestAllDescriptors(nextSequence()))
        Log.i(TAG, "RTBuddy descriptor request #$descriptorRequestAttempts sent=$sent")
        return sent
    }

    @Synchronized
    fun receive(frame: ByteArray) {
        if (!isCurrent()) return
        val data = try {
            val payloadSize = frame.size - HeartRateSensorFrames.HEADER_SIZE
            require(payloadSize >= 0 && payloadSize <= 16_384)
            val declared = (frame[10].toInt() and 255) or ((frame[11].toInt() and 255) shl 8)
            require(declared == payloadSize)
            SensorDataWxCodec.decode(frame.copyOfRange(12, frame.size))
        } catch (error: Exception) {
            Log.w(TAG, "Discarding malformed SensorDataWX frame (${error.javaClass.simpleName})")
            return
        }
        val batch = reports.receive(data)
        if (batch.mappingChanged) {
            Log.i(TAG, "RTBuddy descriptors mapped: motion=${reports.motionService}, heartRate=${reports.heartRateService}")
        }
        if (reports.heartRateService != null) {
            descriptorRequestJob?.cancel()
            descriptorRequestJob = null
        }
        if (batch.mappingChanged && wanted) {
            lastSampleElapsed = 0
            publishState(HeartRateStreamStatus.WAITING)
            stopWire()
            startCampaign()
        }
        val sample = batch.sample ?: return
        if (!wanted) return
        lastSampleElapsed = nowElapsed()
        val capturedAt = System.currentTimeMillis()
        workoutRecorder.add(HeartRateWorkoutSample(sample.bpm, capturedAt))
        state.value = state.value.copy(status = HeartRateStreamStatus.ACTIVE, bpm = sample.bpm,
            sampledAtEpochMillis = capturedAt, workoutStartedAtEpochMillis = workoutStartedAt,
            sampleCount = workoutRecorder.sampleCount(), averageBpm = workoutRecorder.averageBpm(),
            maxBpm = workoutRecorder.maxBpm(), recentSamples = workoutRecorder.recentSamples())
    }

    @Synchronized
    fun startWorkout(workoutType: String = "OTHER"): Boolean {
        if (closed || !isCurrent()) return false
        if (wanted) return true
        wanted = true
        workoutStartedAt = System.currentTimeMillis()
        workoutRecorder.start(workoutStartedAt!!, workoutType)
        headTrackingResumeJob?.cancel()
        headTrackingResumeJob = null
        job?.cancel()
        job = null
        publishState(HeartRateStreamStatus.WAITING)
        startCampaign()
        return true
    }

    @Synchronized
    fun finishWorkout(forceAfterDisconnect: Boolean = false): HeartRateWorkoutSummary? {
        if (closed || (!forceAfterDisconnect && !isCurrent()) || !wanted) return null
        wanted = false
        epoch++
        job?.cancel()
        job = null
        if (!forceAfterDisconnect) {
            stopWire()
            scheduleHeadTrackingResume()
        }
        workoutStartedAt = null
        val summary = workoutRecorder.finish(System.currentTimeMillis())
        state.value = HeartRateStreamState(HeartRateStreamStatus.OFF)
        return summary
    }

    @Synchronized
    fun isWorkoutActive(): Boolean = wanted

    private var workoutStartedAt: Long? = null

    private fun publishState(status: HeartRateStreamStatus) {
        val previous = state.value
        state.value = HeartRateStreamState(
            status = status,
            bpm = previous.bpm,
            sampledAtEpochMillis = previous.sampledAtEpochMillis,
            workoutStartedAtEpochMillis = workoutStartedAt,
            sampleCount = workoutRecorder.sampleCount(),
            averageBpm = workoutRecorder.averageBpm(),
            maxBpm = workoutRecorder.maxBpm(),
            recentSamples = workoutRecorder.recentSamples(),
        )
    }

    private fun heartRateServices(attempt: Int = 0): List<Int> =
        listOf(HEARTRATE_COMMAND, reports.heartRateService ?: legacyHeartRateService(attempt)).distinct()

    private fun startCampaign() {
        val currentEpoch = ++epoch
        job?.cancel()
        job = scope.launch {
            fun active() = synchronized(this@HeartRateSession) {
                currentEpoch == epoch && !closed && wanted && isCurrent()
            }
            try {
                val shouldPauseHeadTracking = synchronized(this@HeartRateSession) {
                    if (headTrackingPauseAttempted) false else {
                        headTrackingPauseAttempted = true
                        true
                    }
                }
                if (shouldPauseHeadTracking) {
                    val paused = runCatching(pauseHeadTracking).getOrElse {
                        Log.w(TAG, "Couldn't pause head tracking before heart-rate start", it)
                        false
                    }
                    synchronized(this@HeartRateSession) {
                        resumeHeadTrackingAfterWorkout = paused
                    }
                    if (paused) {
                        if (active()) delay(HEAD_TRACKING_SWITCH_DELAY_MS)
                        else {
                            scheduleHeadTrackingResume()
                            return@launch
                        }
                    }
                }
                repeat(ATTEMPTS) { attempt ->
                    if (!active()) return@launch
                    if (synchronized(this@HeartRateSession) { reports.heartRateService == null }) {
                        // Keep asking while the workout is active; some firmware delays
                        // descriptor announcements until the RTBuddy channel is warm.
                        requestDescriptors()
                    }
                    synchronized(this@HeartRateSession) { lastSampleElapsed = 0 }
                    for ((packet, pauseMs) in OPEN_SERVICES) {
                        if (!active() || !sendPacket(packet)) return@launch
                        delay(pauseMs)
                    }
                    if (!active() || !sendPacket(HRM_ON)) return@launch
                    val services = synchronized(this@HeartRateSession) {
                        heartRateServices(attempt).also { activeHeartRateServices = it }
                    }
                    Log.i(TAG, "Requesting heart-rate stream from RTBuddy services ${services.joinToString()}")
                    for (service in services) {
                        delay(120)
                        if (!active() || !sendRtBuddy(
                                SensorDataWxCodec.serviceSetting(
                                    sequence = nextSequence(),
                                    logType = HEART_LOG_TYPE,
                                    service = service,
                                    report = 1,
                                    intervalMicros = HEART_RATE_INTERVAL_US,
                                )
                            )
                        ) return@launch
                    }
                    val started = nowElapsed()
                    while (active()) {
                        val last = synchronized(this@HeartRateSession) { lastSampleElapsed }
                        if (nowElapsed() - maxOf(started, last) >= WATCHDOG_MS) break
                        delay(250)
                    }
                    if (!active()) return@launch
                    val attemptResult = if (attempt + 1 < ATTEMPTS) {
                        "retrying heart-rate stream (${attempt + 2}/$ATTEMPTS)"
                    } else {
                        "no further retry available"
                    }
                    Log.w(TAG, "No valid sensor sample on attempt ${attempt + 1}/$ATTEMPTS; $attemptResult")
                    synchronized(this@HeartRateSession) {
                        lastSampleElapsed = 0
                        publishState(HeartRateStreamStatus.WAITING)
                        stopWire()
                    }
                }
            } finally {
                val shouldRestoreHeadTracking = synchronized(this@HeartRateSession) {
                    if (currentEpoch == epoch && !closed && wanted && isCurrent()) {
                        stopWire()
                        wanted = false
                        workoutStartedAt = null
                        publishState(HeartRateStreamStatus.NO_DATA)
                        true
                    } else false
                }
                if (shouldRestoreHeadTracking) scheduleHeadTrackingResume()
            }
        }
    }

    private fun scheduleHeadTrackingResume() {
        val shouldSchedule = synchronized(this) {
            resumeHeadTrackingAfterWorkout && !closed && isCurrent()
        }
        if (!shouldSchedule) return
        headTrackingResumeJob?.cancel()
        headTrackingResumeJob = scope.launch {
            delay(HEAD_TRACKING_SWITCH_DELAY_MS)
            val shouldResume = synchronized(this@HeartRateSession) {
                if (closed || wanted || !isCurrent() || !resumeHeadTrackingAfterWorkout) {
                    false
                } else {
                    resumeHeadTrackingAfterWorkout = false
                    headTrackingPauseAttempted = false
                    true
                }
            }
            if (shouldResume) runCatching(resumeHeadTracking).onFailure {
                Log.w(TAG, "Couldn't resume head tracking after heart-rate workout", it)
            }
        }
    }

    private fun stopWire() {
        if (closed || !isCurrent()) return
        val services = activeHeartRateServices.ifEmpty { heartRateServices() }
        for (service in services) {
            sendRtBuddy(
                SensorDataWxCodec.serviceSetting(
                    sequence = nextSequence(),
                    logType = HEART_LOG_TYPE,
                    service = service,
                    report = 1,
                    intervalMicros = 0,
                )
            )
        }
        sendPacket(HRM_OFF)
        activeHeartRateServices = emptyList()
        lastSampleElapsed = 0
    }

    private fun sendRtBuddy(proto: ByteArray): Boolean = sendPacket(SensorDataWxCodec.wrapRtBuddy(proto))
    private fun nextSequence(): Int = sequence.getAndIncrement() and 0x7fffffff

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        epoch++
        job?.cancel()
        headTrackingResumeJob?.cancel()
        descriptorRequestJob?.cancel()
        scope.cancel()
        wanted = false
        resumeHeadTrackingAfterWorkout = false
        headTrackingPauseAttempted = false
        workoutStartedAt = null
        state.value = HeartRateStreamState(HeartRateStreamStatus.DISCONNECTED)
    }

    companion object {
        private const val TAG = "EchoPodsHeartRate"
        // A freshly opened RTBuddy channel can ignore commands for about 30 seconds.
        // Three bounded attempts cover that cold-start window without leaving the sensor
        // running indefinitely when a device/firmware does not provide samples.
        private const val ATTEMPTS = 3
        private const val WATCHDOG_MS = 12_000L
        private const val DESCRIPTOR_RETRIES = 2
        private const val DESCRIPTOR_RETRY_DELAY_MS = 1_500L
        private const val HEAD_TRACKING_SWITCH_DELAY_MS = 220L
        private const val HEART_LOG_TYPE = 2
        private const val HEARTRATE = 19
        private const val HEARTRATE_COMMAND = 84
        private const val HEART_RATE_INTERVAL_US = 1_000_000
        private val LEGACY_HEART_RATE_SERVICES = listOf(19, 20)
        private val HRM_ON = byteArrayOf(4, 0, 4, 0, 9, 0, 0x30, 1, 0, 0, 0)
        private val HRM_OFF = byteArrayOf(4, 0, 4, 0, 9, 0, 0x30, 2, 0, 0, 0)
        private val OPEN_SERVICES = listOf(
            byteArrayOf(0, 0, 0, 0, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0) to 180L,
            byteArrayOf(4, 0, 0, 0, 1, 0, 0) to 220L,
            byteArrayOf(0, 0, 4, 0, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0) to 180L,
            byteArrayOf(4, 0, 4, 0, 1, 0, 0) to 220L,
        )
        internal fun legacyHeartRateService(attempt: Int): Int =
            LEGACY_HEART_RATE_SERVICES[Math.floorMod(attempt, LEGACY_HEART_RATE_SERVICES.size)]

        private fun nowElapsed() = System.nanoTime() / 1_000_000
    }
}
