package com.example.measurear.ar

import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
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

class ArLineRenderer(
    private val materialLoader: MaterialLoader,
    private val rootNodeConsumer: (Node) -> Unit,
    private val rootNodeRemover: (Node) -> Unit
) {
    private val anchorNodes = mutableListOf<AnchorNode>()
    private val segmentNodes = mutableListOf<CylinderNode>()
    private var previewLineNode: CylinderNode? = null
    private var reticleSphereNode: SphereNode? = null

    private val pointMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(1.0f, 1.0f, 1.0f, 1.0f),
            metallic = 0.0f,
            roughness = 0.3f
        )
    }

    private val lineMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(1.0f, 0.80f, 0.0f, 1.0f),
            metallic = 0.1f,
            roughness = 0.4f
        )
    }

    private val previewMaterial by lazy {
        materialLoader.createColorInstance(
            color = io.github.sceneview.math.Color(0.30f, 0.85f, 0.40f, 0.9f),
            metallic = 0.0f,
            roughness = 0.5f
        )
    }

    fun placeAnchorPoint(session: Session, point: Point3D): Boolean {
        return try {
            val pose = Pose.makeTranslation(point.x, point.y, point.z)
            val anchor: Anchor = session.createAnchor(pose)
            val anchorNode = AnchorNode(materialLoader.engine, anchor)

            val sphere = SphereNode(
                engine = materialLoader.engine,
                radius = 0.0085f,
                materialInstance = pointMaterial
            )
            anchorNode.addChildNode(sphere)
            rootNodeConsumer(anchorNode)
            anchorNodes.add(anchorNode)

            if (anchorNodes.size >= 2) {
                val prevPos = anchorNodes[anchorNodes.size - 2].worldPosition
                val currPos = anchorNodes.last().worldPosition
                val segment = createOrUpdateCylinder(
                    existing = null,
                    start = Point3D(prevPos.x, prevPos.y, prevPos.z),
                    end = Point3D(currPos.x, currPos.y, currPos.z),
                    isPreview = false
                )
                if (segment != null) {
                    segmentNodes.add(segment)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateLivePreviewLine(start: Point3D?, end: Point3D?) {
        if (start == null || end == null) {
            previewLineNode?.let {
                rootNodeRemover(it)
                it.destroy()
            }
            previewLineNode = null
            return
        }
        previewLineNode = createOrUpdateCylinder(
            existing = previewLineNode,
            start = start,
            end = end,
            isPreview = true
        )
    }

    fun updateReticleMarker(reticlePoint: Point3D?, isLocked: Boolean) {
        if (reticlePoint == null || !isLocked) {
            reticleSphereNode?.isVisible = false
            return
        }
        val node = reticleSphereNode ?: SphereNode(
            engine = materialLoader.engine,
            radius = 0.0055f,
            materialInstance = previewMaterial
        ).also {
            rootNodeConsumer(it)
            reticleSphereNode = it
        }
        node.isVisible = true
        node.worldPosition = Float3(reticlePoint.x, reticlePoint.y, reticlePoint.z)
    }

    private fun createOrUpdateCylinder(
        existing: CylinderNode?,
        start: Point3D,
        end: Point3D,
        isPreview: Boolean
    ): CylinderNode? {
        val distance = DistanceCalculator.calculateEuclideanDistance(start, end)
        if (distance < 0.002f) {
            existing?.isVisible = false
            return existing
        }

        val cylinder = existing ?: CylinderNode(
            engine = materialLoader.engine,
            radius = if (isPreview) 0.0025f else 0.0032f,
            height = 1.0f,
            materialInstance = if (isPreview) previewMaterial else lineMaterial
        ).also { rootNodeConsumer(it) }

        cylinder.isVisible = true
        val mid = DistanceCalculator.midpoint(start, end)
        cylinder.worldPosition = Float3(mid.x, mid.y, mid.z)
        cylinder.scale = Float3(1f, distance, 1f)

        // Align cylinder's Y-axis along vector from start to end
        val dirX = (end.x - start.x) / distance
        val dirY = (end.y - start.y) / distance
        val dirZ = (end.z - start.z) / distance
        cylinder.quaternion = quaternionFromUnitYToVector(dirX, dirY, dirZ)

        return cylinder
    }

    private fun quaternionFromUnitYToVector(vx: Float, vy: Float, vz: Float): Quaternion {
        val dot = vy
        if (dot < -0.9999f) {
            return Quaternion(1f, 0f, 0f, 0f)
        }
        val s = sqrt((1f + dot) * 2f)
        val invS = 1f / s
        return Quaternion(
            x = vz * invS,
            y = 0f,
            z = -vx * invS,
            w = s * 0.5f
        )
    }

    fun getAnchoredWorldPoints(): List<Point3D> {
        return anchorNodes.map { node ->
            val pos = node.worldPosition
            Point3D(pos.x, pos.y, pos.z)
        }
    }

    fun undoLastPoint() {
        if (anchorNodes.isNotEmpty()) {
            val lastAnchor = anchorNodes.removeAt(anchorNodes.lastIndex)
            runCatching { lastAnchor.anchor.detach() }
            rootNodeRemover(lastAnchor)
            lastAnchor.destroy()
        }
        if (segmentNodes.isNotEmpty()) {
            val lastSegment = segmentNodes.removeAt(segmentNodes.lastIndex)
            rootNodeRemover(lastSegment)
            lastSegment.destroy()
        }
        updateLivePreviewLine(null, null)
    }

    fun clearAll() {
        anchorNodes.forEach { node ->
            runCatching { node.anchor.detach() }
            rootNodeRemover(node)
            node.destroy()
        }
        anchorNodes.clear()

        segmentNodes.forEach { node ->
            rootNodeRemover(node)
            node.destroy()
        }
        segmentNodes.clear()

        previewLineNode?.let {
            rootNodeRemover(it)
            it.destroy()
        }
        previewLineNode = null
    }

    /**
     * Projects 3D world coordinates into 2D screen pixel coordinates using ARCore's
     * camera view and projection matrices.
     */
    fun projectWorldToScreen(
        camera: Camera,
        viewportWidth: Int,
        viewportHeight: Int,
        point: Point3D
    ): Pair<Float, Float>? {
        if (camera.trackingState != TrackingState.TRACKING || viewportWidth <= 0 || viewportHeight <= 0) {
            return null
        }
        val projMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        val vpMatrix = FloatArray(16)

        camera.getProjectionMatrix(projMatrix, 0, 0.05f, 50f)
        camera.getViewMatrix(viewMatrix, 0)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val worldVec = floatArrayOf(point.x, point.y, point.z, 1f)
        val clipVec = FloatArray(4)
        Matrix.multiplyMV(clipVec, 0, vpMatrix, 0, worldVec, 0)

        if (clipVec[3] <= 0.0001f) return null

        val ndcX = clipVec[0] / clipVec[3]
        val ndcY = clipVec[1] / clipVec[3]

        val screenX = ((ndcX + 1f) * 0.5f) * viewportWidth
        val screenY = ((1f - ndcY) * 0.5f) * viewportHeight
        return screenX to screenY
    }
}
