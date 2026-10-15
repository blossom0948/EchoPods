package com.galaxyairpods.domain.control

/**
 * Small, deterministic recognizer for fresh AAP head-tracking samples.
 * It uses short directional reversals rather than a single noisy sample.
 * Thresholds remain experimental until validated against real firmware.
 */
class HeadGestureRecognizer(
    private val minimumRange: Int = 900,
    private val minimumReversals: Int = 2,
    private val windowMs: Long = 1_200,
    private val maximumFrameGapMs: Long = 250,
    private val maximumFrameAgeMs: Long = 250,
    private val cooldownMs: Long = 1_200,
) {
    private data class Sample(val horizontal: Int, val vertical: Int, val atMs: Long)

    private val samples = ArrayDeque<Sample>()
    private var lastAcceptedAtMs: Long? = null

    fun accept(
        horizontal: Int,
        vertical: Int,
        capturedAtMs: Long,
        nowMs: Long = capturedAtMs,
    ): HeadGesture? {
        if (capturedAtMs <= 0 || capturedAtMs > nowMs || nowMs - capturedAtMs > maximumFrameAgeMs) {
            samples.clear()
            return null
        }
        val previous = samples.lastOrNull()
        if (previous != null) {
            val gap = capturedAtMs - previous.atMs
            if (gap <= 0 || gap > maximumFrameGapMs) samples.clear()
        }
        samples.addLast(Sample(horizontal, vertical, capturedAtMs))
        while (samples.firstOrNull()?.let { capturedAtMs - it.atMs > windowMs } == true) {
            samples.removeFirst()
        }
        if (samples.size < 4) return null
        if (lastAcceptedAtMs?.let { capturedAtMs - it < cooldownMs } == true) return null

        val points = samples.toList()
        val horizontalRange = points.maxOf { it.horizontal } - points.minOf { it.horizontal }
        val verticalRange = points.maxOf { it.vertical } - points.minOf { it.vertical }
        val horizontalReversals = directionReversals(points.map { it.horizontal })
        val verticalReversals = directionReversals(points.map { it.vertical })

        val horizontalConfidence = axisConfidence(horizontalRange, horizontalReversals)
        val verticalConfidence = axisConfidence(verticalRange, verticalReversals)
        val shake = horizontalConfidence >= MIN_CONFIDENCE
        val nod = verticalConfidence >= MIN_CONFIDENCE
        val result = when {
            nod && shake && kotlin.math.abs(verticalConfidence - horizontalConfidence) < MIN_AXIS_CONFIDENCE_DELTA -> null
            nod && shake -> if (verticalConfidence > horizontalConfidence) HeadGesture.NOD else HeadGesture.SHAKE
            nod -> HeadGesture.NOD
            shake -> HeadGesture.SHAKE
            else -> null
        }
        if (result != null) {
            lastAcceptedAtMs = capturedAtMs
            samples.clear()
        }
        return result
    }

    fun reset() {
        samples.clear()
        lastAcceptedAtMs = null
    }

    private fun directionReversals(values: List<Int>): Int {
        var priorDirection = 0
        var reversals = 0
        for (index in 1 until values.size) {
            val delta = values[index] - values[index - 1]
            val direction = when {
                delta > minimumRange / 4 -> 1
                delta < -minimumRange / 4 -> -1
                else -> 0
            }
            if (direction != 0) {
                if (priorDirection != 0 && direction != priorDirection) reversals++
                priorDirection = direction
            }
        }
        return reversals
    }

    private fun axisConfidence(range: Int, reversals: Int): Float {
        if (range < minimumRange || reversals < minimumReversals) return 0f
        return (range.toFloat() / minimumRange) * (reversals.toFloat() / minimumReversals)
    }

    private companion object {
        const val MIN_CONFIDENCE = 1f
        const val MIN_AXIS_CONFIDENCE_DELTA = 0.35f
    }
}
