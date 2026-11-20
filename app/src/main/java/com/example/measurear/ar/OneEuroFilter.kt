package com.example.measurear.ar

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter (Casiez, Pavlotskyi, Roussel — 2012).
 *
 * Adapts its own cutoff frequency to the speed of the signal:
 * - slow/still -> aggressive smoothing (eliminates jitter and breathing)
 * - fast -> smoothing relaxes so it tracks with zero perceived lag.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.03,
    private val dCutoff: Double = 1.0
) {
    private var xFilt: LowPass? = null
    private var dxFilt: LowPass? = null
    private var lastTimeNanos: Long = -1L

    /** @param timestampNanos use frame.timestamp (ARCore camera timestamp), not System.nanoTime(). */
    fun filter(value: Double, timestampNanos: Long): Double {
        if (lastTimeNanos == -1L) {
            lastTimeNanos = timestampNanos
            xFilt = LowPass(value)
            dxFilt = LowPass(0.0)
            return value
        }
        val dt = ((timestampNanos - lastTimeNanos).coerceAtLeast(1L)) / 1_000_000_000.0
        lastTimeNanos = timestampNanos

        val dx = (value - xFilt!!.last()) / dt
        val edx = dxFilt!!.update(dx, alpha(dt, dCutoff))

        val cutoff = minCutoff + beta * abs(edx)
        return xFilt!!.update(value, alpha(dt, cutoff))
    }

    /** Call when the user lifts off / re-picks a point — otherwise the old velocity estimate leaks in. */
    fun reset() {
        lastTimeNanos = -1L
        xFilt = null
        dxFilt = null
    }

    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private class LowPass(initial: Double) {
        private var y = initial
        fun update(value: Double, alpha: Double): Double {
            y = alpha * value + (1 - alpha) * y
            return y
        }
        fun last() = y
    }
}

/**
 * Filters a 3D point component-wise.
 */
class Point3DFilter(minCutoff: Double = 1.0, beta: Double = 0.03) {
    private val fx = OneEuroFilter(minCutoff, beta)
    private val fy = OneEuroFilter(minCutoff, beta)
    private val fz = OneEuroFilter(minCutoff, beta)

    fun filter(x: Float, y: Float, z: Float, timestampNanos: Long): FloatArray = floatArrayOf(
        fx.filter(x.toDouble(), timestampNanos).toFloat(),
        fy.filter(y.toDouble(), timestampNanos).toFloat(),
        fz.filter(z.toDouble(), timestampNanos).toFloat()
    )

    fun filter(point: Point3D, timestampNanos: Long): Point3D {
        val arr = filter(point.x, point.y, point.z, timestampNanos)
        return Point3D(arr[0], arr[1], arr[2])
    }

    fun reset() { fx.reset(); fy.reset(); fz.reset() }
}
