package com.example.measurear.ar

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.sqrt

data class FrameHitUpdate(
    val hitPoint: Point3D?,
    val isPlaneLocked: Boolean,
    val trackingHint: TrackingQualityHint
)

class ArMeasureSession {

    fun checkArCoreAvailability(context: Context): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability.isSupported
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Evaluates an ARCore [Frame] at the center of the viewport.
     * Supports standard plane/point hit-testing as well as vertical ray-plane intersection
     * when measuring vertical height above a locked floor anchor.
     */
    fun processFrame(
        frame: Frame,
        viewportWidth: Int,
        viewportHeight: Int,
        measureMode: MeasureMode,
        placedPoints: List<Point3D>
    ): FrameHitUpdate {
        val camera = frame.camera
        val trackingHint = resolveTrackingHint(camera)

        if (camera.trackingState != TrackingState.TRACKING || viewportWidth <= 0 || viewportHeight <= 0) {
            return FrameHitUpdate(
                hitPoint = null,
                isPlaneLocked = false,
                trackingHint = trackingHint
            )
        }

        // In Height Mode, once the floor point (Point A) is placed, project the camera's
        // center look ray onto a vertical axis directly above/below Point A.
        if (measureMode == MeasureMode.HEIGHT && placedPoints.size == 1) {
            val floorPoint = placedPoints.first()
            val verticalPoint = computeVerticalHeightTarget(camera, floorPoint)
            if (verticalPoint != null) {
                return FrameHitUpdate(
                    hitPoint = verticalPoint,
                    isPlaneLocked = true,
                    trackingHint = TrackingQualityHint.NONE
                )
            }
        }

        val centerX = viewportWidth * 0.5f
        val centerY = viewportHeight * 0.5f
        val hitResults = frame.hitTest(centerX, centerY)

        // Prioritize tracked Planes within polygon bounds, then feature points
        val planeHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(hit.hitPose)
        }

        val featureHit = hitResults.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Point &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
        }

        val chosenHit = planeHit ?: featureHit
        return if (chosenHit != null) {
            val pose = chosenHit.hitPose
            FrameHitUpdate(
                hitPoint = Point3D(pose.tx(), pose.ty(), pose.tz()),
                isPlaneLocked = planeHit != null,
                trackingHint = TrackingQualityHint.NONE
            )
        } else {
            FrameHitUpdate(
                hitPoint = null,
                isPlaneLocked = false,
                trackingHint = if (trackingHint == TrackingQualityHint.NONE) {
                    TrackingQualityHint.INITIALIZING
                } else {
                    trackingHint
                }
            )
        }
    }

    /**
     * Intersects the camera's forward look ray with a vertical plane containing [floorPoint]
     * and facing the camera, locking X and Z to [floorPoint] so the line rises straight up.
     */
    private fun computeVerticalHeightTarget(camera: Camera, floorPoint: Point3D): Point3D? {
        val camPose = camera.displayOrientedPose
        val camPos = Point3D(camPose.tx(), camPose.ty(), camPose.tz())

        // Camera forward vector is -Z axis in OpenGL/ARCore camera space
        val zAxis = camPose.zAxis
        val rayDirX = -zAxis[0]
        val rayDirY = -zAxis[1]
        val rayDirZ = -zAxis[2]

        // Normal in horizontal XZ plane from floorPoint toward camera
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
