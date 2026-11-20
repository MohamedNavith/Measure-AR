package com.example.measurear.ar

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteOrder
import java.util.EnumSet
import kotlin.math.sqrt

sealed class FrameHitUpdate {
    data class Found(val point: Point3D, val source: HitSource) : FrameHitUpdate()
    object NotTracking : FrameHitUpdate()
    object NoHit : FrameHitUpdate()

    val hitPoint: Point3D?
        get() = (this as? Found)?.point

    val isPlaneLocked: Boolean
        get() = (this as? Found)?.let { it.source == HitSource.PLANE || it.source == HitSource.RAW_DEPTH } ?: false

    val isEdgeSnapped: Boolean
        get() = false

    val trackingHint: TrackingQualityHint
        get() = when (this) {
            is NotTracking -> TrackingQualityHint.INITIALIZING
            else -> TrackingQualityHint.NONE
        }
}

enum class HitSource { PLANE, FEATURE_POINT, INSTANT_PLACEMENT, RAW_DEPTH }

/**
 * ============================================================================
 * PROBLEM B FIX — "Surfaces are not scannable"
 * ============================================================================
 * Solved via 3-stage fallback:
 *   1. Standard hitTest against existing Plane/Point trackables.
 *   2. hitTestInstantPlacement — works with zero planes, using depth estimate.
 *   3. Raw Depth API pixel sample at the exact tap/reticle location — solves
 *      untextured blank walls and floors with no feature points.
 * ============================================================================
 */
class ArMeasureSession {

    // Seed instant-placement with the last depth we actually measured
    private var lastKnownDepthMeters: Float = 1.0f

    fun checkArCoreAvailability(context: Context): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability.isSupported
        } catch (e: Exception) {
            false
        }
    }

    fun configureSession(session: Session) {
        val config = session.config

        // Continuous autofocus for near-field clarity
        config.focusMode = Config.FocusMode.AUTO
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            Config.DepthMode.AUTOMATIC
        } else {
            Config.DepthMode.DISABLED
        }

        try {
            val filter = CameraConfigFilter(session)
                .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30, CameraConfig.TargetFps.TARGET_FPS_60))
            session.getSupportedCameraConfigs(filter)
                .maxByOrNull { it.fpsRange.upper }
                ?.let { session.cameraConfig = it }
        } catch (_: Exception) {
            // Fallback to default camera configuration on devices with locked configs
        }

        session.configure(config)
    }

    fun configureSessionFeatures(session: Session, config: Config) {
        configureSession(session)
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

    /**
     * Call once per frame. viewWidth/viewHeight are the GL surface dimensions.
     */
    fun processFrame(
        session: Session,
        frame: Frame,
        viewWidth: Int,
        viewHeight: Int,
        screenX: Float = viewWidth * 0.5f,
        screenY: Float = viewHeight * 0.5f
    ): FrameHitUpdate {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING || viewWidth <= 0 || viewHeight <= 0) {
            return FrameHitUpdate.NotTracking
        }

        // --- Stage 1: standard hit test against tracked Plane/Point trackables ---
        for (hit in frame.hitTest(screenX, screenY)) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) &&
                trackable.trackingState == TrackingState.TRACKING
            ) {
                val p = hit.hitPose
                lastKnownDepthMeters = distanceToCamera(camera, p.tx(), p.ty(), p.tz())
                return FrameHitUpdate.Found(Point3D(p.tx(), p.ty(), p.tz()), HitSource.PLANE)
            }
            if (trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) {
                val p = hit.hitPose
                lastKnownDepthMeters = distanceToCamera(camera, p.tx(), p.ty(), p.tz())
                return FrameHitUpdate.Found(Point3D(p.tx(), p.ty(), p.tz()), HitSource.FEATURE_POINT)
            }
        }

        // --- Stage 2: instant placement, no plane required ---
        frame.hitTestInstantPlacement(screenX, screenY, lastKnownDepthMeters).firstOrNull()?.let { hit ->
            val p = hit.hitPose
            return FrameHitUpdate.Found(Point3D(p.tx(), p.ty(), p.tz()), HitSource.INSTANT_PLACEMENT)
        }

        // --- Stage 3: raw depth-map sample for untextured surfaces (blank walls/floors) ---
        if (session.config.depthMode != Config.DepthMode.DISABLED) {
            try {
                frame.acquireDepthImage16Bits().use { depthImage ->
                    sampleDepthAsWorldPoint(depthImage, camera, screenX, screenY, viewWidth, viewHeight)
                        ?.let { point ->
                            lastKnownDepthMeters = distanceToCamera(camera, point.x, point.y, point.z)
                            return FrameHitUpdate.Found(point, HitSource.RAW_DEPTH)
                        }
                }
            } catch (_: NotYetAvailableException) {
                // Depth image not ready yet this frame
            } catch (_: Exception) {
                // Ignore transient frame acquisition issues
            }
        }

        return FrameHitUpdate.NoHit
    }

    private fun distanceToCamera(camera: Camera, x: Float, y: Float, z: Float): Float {
        val pose = camera.pose
        val dx = x - pose.tx()
        val dy = y - pose.ty()
        val dz = z - pose.tz()
        return sqrt(dx * dx + dy * dy + dz * dz).coerceIn(0.1f, 8.0f)
    }

    /**
     * Unprojects one pixel of the ARCore depth map into a world-space point using camera intrinsics.
     */
    private fun sampleDepthAsWorldPoint(
        depthImage: android.media.Image,
        camera: Camera,
        screenX: Float,
        screenY: Float,
        viewWidth: Int,
        viewHeight: Int
    ): Point3D? {
        val depthWidth = depthImage.width
        val depthHeight = depthImage.height
        val depthX = (screenX / viewWidth * depthWidth).toInt().coerceIn(0, depthWidth - 1)
        val depthY = (screenY / viewHeight * depthHeight).toInt().coerceIn(0, depthHeight - 1)

        val plane = depthImage.planes[0]
        val buffer = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val rowStrideShorts = plane.rowStride / 2
        val millimeters = buffer[depthY * rowStrideShorts + depthX].toInt() and 0xFFFF
        if (millimeters <= 0) return null
        val depthMeters = millimeters / 1000f

        val intrinsics = camera.imageIntrinsics
        val focalLength = intrinsics.focalLength        // [fx, fy]
        val principalPoint = intrinsics.principalPoint   // [cx, cy]
        val imageDims = intrinsics.imageDimensions       // [width, height]

        val scaledX = depthX.toFloat() / depthWidth * imageDims[0]
        val scaledY = depthY.toFloat() / depthHeight * imageDims[1]
        val nx = (scaledX - principalPoint[0]) / focalLength[0]
        val ny = (scaledY - principalPoint[1]) / focalLength[1]

        val cameraSpace = floatArrayOf(nx * depthMeters, -ny * depthMeters, -depthMeters)
        val worldSpace = FloatArray(3)
        camera.pose.transformPoint(cameraSpace, 0, worldSpace, 0)
        return Point3D(worldSpace[0], worldSpace[1], worldSpace[2])
    }
}
