package com.example.measurear.ar

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt

data class FrameHitUpdate(
    val hitPoint: Point3D?,
    val isPlaneLocked: Boolean,
    val isEdgeSnapped: Boolean,
    val trackingHint: TrackingQualityHint,
    val detectedSurfaceExtent: Pair<Float, Float>? = null
)

class ArMeasureSession {

    private var lastSmoothedPoint: Point3D? = null
    private var lastEdgeSnapped: Boolean = false

    fun checkArCoreAvailability(context: Context): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability.isSupported
        } catch (e: Exception) {
            false
        }
    }

    fun configureSessionFeatures(session: Session, config: Config) {
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

        // Enable Instant Placement for instant hit testing without waiting for full planes
        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP

        // Enable Depth API if supported for mm-accurate depth mapping
        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            Config.DepthMode.AUTOMATIC
        } else {
            Config.DepthMode.DISABLED
        }

        config.focusMode = Config.FocusMode.AUTO
    }

    fun setTorchMode(session: Session, enabled: Boolean): Boolean {
        return try {
            val config = session.config
            config.flashMode = if (enabled) Config.FlashMode.TORCH else Config.FlashMode.OFF
            session.configure(config)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setFocusLock(session: Session, locked: Boolean): Boolean {
        return try {
            val config = session.config
            config.focusMode = if (locked) Config.FocusMode.FIXED else Config.FocusMode.AUTO
            session.configure(config)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun processFrame(
        session: Session,
        frame: Frame,
        viewportWidth: Int,
        viewportHeight: Int,
        measureMode: MeasureMode,
        placedPoints: List<Point3D>
    ): FrameHitUpdate {
        val camera = frame.camera
        val trackingHint = resolveTrackingHint(camera)

        if (camera.trackingState != TrackingState.TRACKING || viewportWidth <= 0 || viewportHeight <= 0) {
            lastSmoothedPoint = null
            return FrameHitUpdate(
                hitPoint = null,
                isPlaneLocked = false,
                isEdgeSnapped = false,
                trackingHint = trackingHint
            )
        }

        // In Height Mode, once Point A is placed, project center ray onto vertical axis
        if (measureMode == MeasureMode.HEIGHT && placedPoints.size == 1) {
            val floorPoint = placedPoints.first()
            val verticalPoint = computeVerticalHeightTarget(camera, floorPoint)
            if (verticalPoint != null) {
                lastSmoothedPoint = verticalPoint
                return FrameHitUpdate(
                    hitPoint = verticalPoint,
                    isPlaneLocked = true,
                    isEdgeSnapped = false,
                    trackingHint = TrackingQualityHint.NONE
                )
            }
        }

        val centerX = viewportWidth * 0.5f
        val centerY = viewportHeight * 0.5f
        val hitResults = frame.hitTest(centerX, centerY)

        // 1. Plane polygon hit (highest confidence)
        val planeHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(hit.hitPose)
        }

        // 2. Instant placement hit (instant responsive surface tracking)
        val instantHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is InstantPlacementPoint &&
                trackable.trackingState == TrackingState.TRACKING
        }

        // 3. General plane hit within extent bounds
        val generalPlaneHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane && trackable.trackingState == TrackingState.TRACKING
        }

        // 4. Feature point hit
        val featureHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Point && trackable.trackingState == TrackingState.TRACKING
        }

        val chosenHit = planeHit ?: instantHit ?: generalPlaneHit ?: featureHit

        if (chosenHit == null) {
            lastSmoothedPoint = null
            return FrameHitUpdate(
                hitPoint = null,
                isPlaneLocked = false,
                isEdgeSnapped = false,
                trackingHint = if (trackingHint == TrackingQualityHint.NONE) TrackingQualityHint.INITIALIZING else trackingHint
            )
        }

        val hitPose = chosenHit.hitPose
        val rawPoint = Point3D(hitPose.tx(), hitPose.ty(), hitPose.tz())

        // Extract plane extent for auto surface dimension detection
        var surfaceExtent: Pair<Float, Float>? = null
        val targetPlane = (chosenHit.trackable as? Plane)
        if (targetPlane != null && targetPlane.trackingState == TrackingState.TRACKING) {
            surfaceExtent = targetPlane.extentX to targetPlane.extentZ
        }

        // Edge Detection & Magnetic Snapping
        val edgeSegments = extractPlaneEdgeSegments(session)
        val (edgeSnappedPoint, didEdgeSnap) = DistanceCalculator.snapToNearestEdge(
            point = rawPoint,
            edgeSegments = edgeSegments,
            thresholdMeters = 0.045f
        )

        // Axis / Level Alignment Snapping (when extending from last point)
        val candidatePoint = if (didEdgeSnap) {
            edgeSnappedPoint
        } else if (placedPoints.isNotEmpty()) {
            val (axisSnapped, didAxisSnap) = DistanceCalculator.snapToLevelAxis(
                start = placedPoints.last(),
                current = rawPoint,
                thresholdDegrees = 3.5f
            )
            if (didAxisSnap) axisSnapped else rawPoint
        } else {
            rawPoint
        }

        // Low-pass EMA filter to eliminate distance drift and lens breathing
        val finalPoint = if (didEdgeSnap || lastSmoothedPoint == null) {
            candidatePoint
        } else {
            val prev = lastSmoothedPoint!!
            val moveDelta = DistanceCalculator.calculateEuclideanDistance(prev, candidatePoint)
            // If moved significantly, track fast; if stationary, smooth aggressively
            val alpha = if (moveDelta > 0.06f) 0.85f else 0.32f
            Point3D(
                x = prev.x * (1f - alpha) + candidatePoint.x * alpha,
                y = prev.y * (1f - alpha) + candidatePoint.y * alpha,
                z = prev.z * (1f - alpha) + candidatePoint.z * alpha
            )
        }

        lastSmoothedPoint = finalPoint
        lastEdgeSnapped = didEdgeSnap

        return FrameHitUpdate(
            hitPoint = finalPoint,
            isPlaneLocked = planeHit != null || generalPlaneHit != null,
            isEdgeSnapped = didEdgeSnap,
            trackingHint = TrackingQualityHint.NONE,
            detectedSurfaceExtent = surfaceExtent
        )
    }

    private fun extractPlaneEdgeSegments(session: Session): List<Pair<Point3D, Point3D>> {
        val segments = mutableListOf<Pair<Point3D, Point3D>>()
        val planes = session.getAllTrackables(Plane::class.java)
        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
            val polygon: FloatBuffer = plane.polygon ?: continue
            val vertices = mutableListOf<Point3D>()
            polygon.rewind()
            while (polygon.hasRemaining()) {
                val x = polygon.get()
                val z = polygon.get()
                val world = plane.centerPose.transformPoint(floatArrayOf(x, 0f, z))
                vertices.add(Point3D(world[0], world[1], world[2]))
            }
            if (vertices.size >= 2) {
                for (i in 0 until vertices.size) {
                    val next = vertices[(i + 1) % vertices.size]
                    segments.add(vertices[i] to next)
                }
            }
        }
        return segments
    }

    private fun computeVerticalHeightTarget(camera: Camera, floorPoint: Point3D): Point3D? {
        val camPose = camera.displayOrientedPose
        val camPos = Point3D(camPose.tx(), camPose.ty(), camPose.tz())

        val zAxis = camPose.zAxis
        val rayDirX = -zAxis[0]
        val rayDirY = -zAxis[1]
        val rayDirZ = -zAxis[2]

        val toCamX = camPos.x - floorPoint.x
        val toCamZ = camPos.z - floorPoint.z
        val horizDist = sqrt(toCamX * toCamX + toCamZ * toCamZ)
        if (horizDist < 0.05f) return null

        val normX = toCamX / horizDist
        val normZ = toCamZ / horizDist

        val denom = rayDirX * normX + rayDirZ * normZ
        if (abs(denom) < 0.001f) return null

        val t = ((floorPoint.x - camPos.x) * normX + (floorPoint.z - camPos.z) * normZ) / denom
        if (t <= 0f) return null

        val targetY = (camPos.y + rayDirY * t).coerceIn(floorPoint.y - 5f, floorPoint.y + 10f)
        return Point3D(
            x = floorPoint.x,
            y = targetY,
            z = floorPoint.z
        )
    }

    private fun resolveTrackingHint(camera: Camera): TrackingQualityHint {
        if (camera.trackingState == TrackingState.TRACKING) {
            return TrackingQualityHint.NONE
        }
        return when (camera.trackingFailureReason) {
            TrackingFailureReason.NONE -> TrackingQualityHint.INITIALIZING
            TrackingFailureReason.BAD_STATE -> TrackingQualityHint.INITIALIZING
            TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingQualityHint.TOO_DARK
            TrackingFailureReason.EXCESSIVE_MOTION -> TrackingQualityHint.TOO_FAST
            TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingQualityHint.INSUFFICIENT_FEATURES
            TrackingFailureReason.CAMERA_UNAVAILABLE -> TrackingQualityHint.CAMERA_UNAVAILABLE
        }
    }
}
