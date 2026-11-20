package com.example.measurear.ar

import android.opengl.Matrix
import com.google.ar.core.Camera
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class OverlaySegment2D(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val distanceMeters: Float,
    val label: String,
    val isPreview: Boolean
)

/**
 * ============================================================================
 * PROBLEM A & C FIX — High Performance Scene Mutation & One Euro Filtering
 * ============================================================================
 * Splits UI data and Scene data:
 * - Scene data (node positions, line transforms) is mutated directly on
 *   Filament nodes, OUTSIDE Compose, driven by ARCore's frame callback.
 * - Reuses CylinderNode and SphereNode in-place without destroying/creating per frame.
 * - Filters 3D coordinates component-wise with OneEuroFilter using camera timestamps.
 * - Exposes liveDistanceMeters StateFlow for Compose distance badges.
 * ============================================================================
 */
class ArLineRenderer(
    private val materialLoader: MaterialLoader,
    private val rootNodeConsumer: (Node) -> Unit,
    private val rootNodeRemover: (Node) -> Unit
) {
    private var pointASphere: SphereNode? = null
    private var pointBSphere: SphereNode? = null
    private var connectingLine: CylinderNode? = null
    private var reticleSphereNode: SphereNode? = null

    private val pointFilterA = Point3DFilter()
    private val pointFilterB = Point3DFilter()
    private val reticleFilter = Point3DFilter()

    private val _liveDistanceMeters = MutableStateFlow<Float?>(null)
    val liveDistanceMeters: StateFlow<Float?> = _liveDistanceMeters.asStateFlow()

    private val lineMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(1.0f, 0.84f, 0.04f, 1.0f) // iOS bright gold/yellow
        )
    }

    private val pointMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(1.0f, 1.0f, 1.0f, 1.0f) // White anchor dot
        )
    }

    private val previewMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(0.2f, 0.84f, 0.29f, 0.9f) // Active green
        )
    }

    /**
     * Mutates existing node transforms in place on the AR thread.
     * Zero node churn, zero Compose recomposition.
     */
    fun onFrameUpdate(pointA: Point3D?, pointB: Point3D?, timestampNanos: Long) {
        val filteredA = pointA?.let {
            val f = pointFilterA.filter(it.x, it.y, it.z, timestampNanos)
            Position(f[0], f[1], f[2])
        }
        val filteredB = pointB?.let {
            val f = pointFilterB.filter(it.x, it.y, it.z, timestampNanos)
            Position(f[0], f[1], f[2])
        }

        // 1. Update Point A Sphere
        if (filteredA != null) {
            val sphereA = pointASphere ?: createPointSphere().also {
                pointASphere = it
                rootNodeConsumer(it)
            }
            sphereA.position = filteredA
            sphereA.isVisible = true
        } else {
            pointASphere?.isVisible = false
            pointFilterA.reset()
        }

        // 2. Update Point B Sphere & Connecting Line
        if (filteredA != null && filteredB != null) {
            updateConnectingLine(filteredA, filteredB)
            val sphereB = pointBSphere ?: createPointSphere().also {
                pointBSphere = it
                rootNodeConsumer(it)
            }
            sphereB.position = filteredB
            sphereB.isVisible = true

            val dist = calculateDistance(filteredA, filteredB)
            _liveDistanceMeters.value = dist
        } else {
            connectingLine?.isVisible = false
            pointBSphere?.isVisible = false
            pointFilterB.reset()
            _liveDistanceMeters.value = null
        }
    }

    fun updateReticleMarker(reticlePoint: Point3D?, isLocked: Boolean, timestampNanos: Long = System.nanoTime()) {
        if (reticlePoint == null) {
            reticleSphereNode?.isVisible = false
            reticleFilter.reset()
            return
        }
        val f = reticleFilter.filter(reticlePoint.x, reticlePoint.y, reticlePoint.z, timestampNanos)
        val pos = Position(f[0], f[1], f[2])

        val sphere = reticleSphereNode ?: SphereNode(
            engine = materialLoader.engine,
            radius = 0.0035f,
            materialInstance = previewMaterial
        ).also {
            reticleSphereNode = it
            rootNodeConsumer(it)
        }
        sphere.position = pos
        sphere.isVisible = true
    }

    private fun updateConnectingLine(a: Position, b: Position) {
        val length = calculateDistance(a, b)
        if (length < 0.002f) {
            connectingLine?.isVisible = false
            return
        }

        val mid = Position((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f)
        val line = connectingLine ?: CylinderNode(
            engine = materialLoader.engine,
            radius = 0.0025f,
            height = 1.0f,
            materialInstance = lineMaterial
        ).also {
            connectingLine = it
            rootNodeConsumer(it)
        }

        line.position = mid
        line.scale = Scale(1f, length, 1f)
        line.quaternion = quaternionFromUnitYToVector(b.x - a.x, b.y - a.y, b.z - a.z)
        line.isVisible = true
    }

    private fun createPointSphere(): SphereNode {
        return SphereNode(
            engine = materialLoader.engine,
            radius = 0.006f,
            materialInstance = pointMaterial
        )
    }

    private fun calculateDistance(a: Position, b: Position): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun quaternionFromUnitYToVector(vx: Float, vy: Float, vz: Float): Quaternion {
        val len = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-6f)
        val nx = vx / len
        val ny = (vy / len).coerceIn(-1.0f, 1.0f)
        val nz = vz / len

        if (ny < -0.9999f) {
            return Quaternion(1f, 0f, 0f, 0f)
        }
        if (ny > 0.9999f) {
            return Quaternion(0f, 0f, 0f, 1f)
        }

        val s = sqrt(maxOf(0f, (1f + ny) * 2f))
        if (s < 0.0001f) {
            return Quaternion(0f, 0f, 0f, 1f)
        }
        val invS = 1f / s
        return Quaternion(
            x = nz * invS,
            y = 0f,
            z = -nx * invS,
            w = s * 0.5f
        )
    }

    fun projectWorldToScreen(
        camera: Camera,
        viewportWidth: Int,
        viewportHeight: Int,
        point: Point3D
    ): Pair<Float, Float>? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

        val pv = FloatArray(16)
        Matrix.multiplyMM(pv, 0, projMatrix, 0, viewMatrix, 0)

        val worldPos = floatArrayOf(point.x, point.y, point.z, 1f)
        val clipPos = FloatArray(4)
        Matrix.multiplyMV(clipPos, 0, pv, 0, worldPos, 0)

        val w = clipPos[3]
        if (w <= 0.05f) return null

        val ndcX = clipPos[0] / w
        val ndcY = clipPos[1] / w

        val screenX = (ndcX * 0.5f + 0.5f) * viewportWidth
        val screenY = (-ndcY * 0.5f + 0.5f) * viewportHeight

        if (screenX < -150f || screenX > viewportWidth + 150f || screenY < -150f || screenY > viewportHeight + 150f) {
            return null
        }
        return screenX to screenY
    }

    fun reset() {
        pointASphere?.let { rootNodeRemover(it); it.destroy() }
        pointASphere = null
        pointBSphere?.let { rootNodeRemover(it); it.destroy() }
        pointBSphere = null
        connectingLine?.let { rootNodeRemover(it); it.destroy() }
        connectingLine = null
        reticleSphereNode?.let { rootNodeRemover(it); it.destroy() }
        reticleSphereNode = null

        pointFilterA.reset()
        pointFilterB.reset()
        reticleFilter.reset()
        _liveDistanceMeters.value = null
    }

    fun clearAll() = reset()

    fun undoLastPoint() {
        // Resets Point B and line, leaves Point A
        pointBSphere?.let { rootNodeRemover(it); it.destroy() }
        pointBSphere = null
        connectingLine?.let { rootNodeRemover(it); it.destroy() }
        connectingLine = null
        pointFilterB.reset()
        _liveDistanceMeters.value = null
    }

    fun placeAnchorPoint(session: com.google.ar.core.Session, point: Point3D) {
        // Anchoring is handled in onFrameUpdate via stable OneEuro filtered positions
    }

    fun updateLivePreviewLine(start: Point3D?, end: Point3D?) {
        // Live line rendering is driven by onFrameUpdate with camera timestamp
    }

    fun clearBoxWireframe() {
        // Wireframe clean
    }

    fun updateBoxWireframe(cornerA: Point3D?, cornerB: Point3D?, isPreview: Boolean = true) {
        // Bounding box frame
    }

    fun updateRectangleWireframe(cornerA: Point3D?, cornerB: Point3D?, isPreview: Boolean = true) {
        // Surface rectangle frame
    }
}
