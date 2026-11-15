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
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reports = HeartRateSensorReports()
    private val workoutRecorder = HeartRateWorkoutRecorder()
    private val sequence = AtomicInteger(0)
    private var descriptorRequested = false
    private var wanted = false
    private var closed = false
    private var lastSampleElapsed = 0L
    private var job: Job? = null
    private var epoch = 0L

    private fun isCurrent() = !closed && socket.isConnected && BluetoothConnectionManager.aacpSocket === socket

    @Synchronized
    fun requestDescriptors() {
        if (closed || descriptorRequested || !isCurrent()) return
        descriptorRequested = sendRtBuddy(SensorDataWxCodec.requestAllDescriptors(nextSequence()))
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
        if (batch.mappingChanged && wanted) {
            lastSampleElapsed = 0
            publishState(HeartRateStreamStatus.WAITING)
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
        if (!forceAfterDisconnect) stopWire()
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

    private fun services(): List<Int> = reports.heartRateService?.let(::listOf)
        ?: listOf(HEARTRATE_COMMAND, HEARTRATE)

    private fun startCampaign() {
        val currentEpoch = ++epoch
        job?.cancel()
        job = scope.launch {
            fun active() = synchronized(this@HeartRateSession) {
                currentEpoch == epoch && !closed && wanted && isCurrent()
            }
            try {
                repeat(ATTEMPTS) {
                    if (!active()) return@launch
                    synchronized(this@HeartRateSession) { lastSampleElapsed = 0 }
                    for ((packet, pauseMs) in OPEN_SERVICES) {
                        if (!active() || !sendPacket(packet)) return@launch
                        delay(pauseMs)
                    }
                    if (!active() || !sendPacket(HRM_ON)) return@launch
                    for (service in synchronized(this@HeartRateSession) { services() }) {
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
                    Log.w(TAG, "No valid sensor sample; retrying the heart-rate stream once")
                    synchronized(this@HeartRateSession) {
                        lastSampleElapsed = 0
                        publishState(HeartRateStreamStatus.WAITING)
                        stopWire()
                    }
                }
            } finally {
                synchronized(this@HeartRateSession) {
                    if (currentEpoch == epoch && !closed && wanted && isCurrent()) {
                        stopWire()
                        wanted = false
                        workoutStartedAt = null
                        publishState(HeartRateStreamStatus.NO_DATA)
                    }
                }
            }
        }
    }

    private fun stopWire() {
        if (closed || !isCurrent()) return
        for (service in services()) {
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
        scope.cancel()
        wanted = false
        workoutStartedAt = null
        state.value = HeartRateStreamState(HeartRateStreamStatus.DISCONNECTED)
    }

    companion object {
        private const val TAG = "EchoPodsHeartRate"
        private const val ATTEMPTS = 2
        private const val WATCHDOG_MS = 12_000L
        private const val HEART_LOG_TYPE = 2
        private const val HEARTRATE = 19
        private const val HEARTRATE_COMMAND = 84
        private const val HEART_RATE_INTERVAL_US = 1_000_000
        private val HRM_ON = byteArrayOf(4, 0, 4, 0, 9, 0, 0x30, 1, 0, 0, 0)
        private val HRM_OFF = byteArrayOf(4, 0, 4, 0, 9, 0, 0x30, 2, 0, 0, 0)
        private val OPEN_SERVICES = listOf(
            byteArrayOf(0, 0, 0, 0, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0) to 180L,
            byteArrayOf(4, 0, 0, 0, 1, 0, 0) to 220L,
            byteArrayOf(0, 0, 4, 0, 1, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0) to 180L,
            byteArrayOf(4, 0, 4, 0, 1, 0, 0) to 220L,
        )
        private fun nowElapsed() = System.nanoTime() / 1_000_000
    }
}
