package com.example.measurear.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.measurear.ar.ArLineRenderer
import com.example.measurear.ar.ArMeasureSession
import com.example.measurear.ar.DeviceLevelData
import com.example.measurear.ar.GyroOrientationSensor
import com.example.measurear.ar.MeasureMode
import com.example.measurear.ar.OverlaySegment2D
import com.example.measurear.ar.Point3D
import com.example.measurear.ar.TrackingQualityHint
import com.example.measurear.ui.components.AnimatedCenterReticle
import com.example.measurear.ui.components.DistanceBadgePill
import com.example.measurear.ui.components.GlassmorphicCard
import com.example.measurear.ui.components.ShutterAddPointButton
import com.example.measurear.ui.components.StatusInstructionPill
import com.example.measurear.ui.settings.SettingsSheet
import com.example.measurear.ui.theme.ArGreen
import com.example.measurear.ui.theme.ArYellow
import com.example.measurear.ui.theme.TabularMeasureTextStyle
import com.example.measurear.util.BitmapShareHelper
import com.example.measurear.util.UnitConverter
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MeasureCameraScreen(
    viewModel: MeasureViewModel,
    arMeasureSession: ArMeasureSession,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionDeniedPermanently = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isArSupported = remember { arMeasureSession.checkArCoreAvailability(context) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        PermissionRequiredFallback(
            isPermanent = permissionDeniedPermanently,
            onRequestAgain = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
        )
        return
    }

    if (!isArSupported) {
        ArUnsupportedFallback()
        return
    }

    // SceneView Engine & Nodes
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()

    var activeSession by remember { mutableStateOf<Session?>(null) }
    var arSceneViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var projectedSegments by remember { mutableStateOf<List<OverlaySegment2D>>(emptyList()) }
    var detectedSurfaceDots by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var wasEdgeSnapped by remember { mutableStateOf(false) }

    val gyroSensor = remember { GyroOrientationSensor(context) }
    val levelData by gyroSensor.levelState.collectAsStateWithLifecycle()

    DisposableEffect(gyroSensor) {
        gyroSensor.startListening()
        onDispose {
            gyroSensor.stopListening()
        }
    }

    val lineRenderer = remember(materialLoader) {
        ArLineRenderer(
            materialLoader = materialLoader,
            rootNodeConsumer = { node -> childNodes.add(node) },
            rootNodeRemover = { node -> childNodes.remove(node) }
        )
    }

    DisposableEffect(lineRenderer) {
        onDispose {
            lineRenderer.clearAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
    ) {
        // 1. Full-Screen ARSceneView
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            childNodes = childNodes,
            planeRenderer = false, // We draw a custom subtle iOS-style dot grid
            sessionConfiguration = { session, config ->
                activeSession = session
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
                config.focusMode = Config.FocusMode.AUTO
            },
            onViewCreated = {
                arSceneViewRef = this
            },
            onSessionUpdated = { session, frame ->
                activeSession = session
                val hitUpdate = arMeasureSession.processFrame(
                    session = session,
                    frame = frame,
                    viewportWidth = viewportSize.width,
                    viewportHeight = viewportSize.height,
                    measureMode = uiState.mode,
                    placedPoints = uiState.placedPoints
                )

                if (hitUpdate.isEdgeSnapped && !wasEdgeSnapped && settings.hapticFeedbackEnabled) {
                    localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                wasEdgeSnapped = hitUpdate.isEdgeSnapped

                viewModel.onFrameHitUpdated(
                    hitPoint = hitUpdate.hitPoint,
                    isPlaneLocked = hitUpdate.isPlaneLocked,
                    isEdgeSnapped = hitUpdate.isEdgeSnapped,
                    trackingHint = hitUpdate.trackingHint
                )

                // Update 3D reticle sphere & 3D preview cylinder
                lineRenderer.updateReticleMarker(
                    reticlePoint = hitUpdate.hitPoint,
                    isLocked = hitUpdate.isPlaneLocked
                )

                // Update procedural 3D wireframe according to mode
                when (uiState.mode) {
                    MeasureMode.BOX_3D -> {
                        lineRenderer.updateLivePreviewLine(null, null)
                        if (uiState.stepState == MeasureStepState.Live && uiState.placedPoints.isNotEmpty()) {
                            lineRenderer.updateBoxWireframe(
                                cornerA = uiState.placedPoints.first(),
                                cornerB = hitUpdate.hitPoint,
                                isPreview = true
                            )
                        } else if (uiState.stepState == MeasureStepState.Done && uiState.placedPoints.size >= 2) {
                            lineRenderer.updateBoxWireframe(
                                cornerA = uiState.placedPoints[0],
                                cornerB = uiState.placedPoints[1],
                                isPreview = false
                            )
                        } else {
                            lineRenderer.clearBoxWireframe()
                        }
                    }
                    MeasureMode.RECTANGLE -> {
                        lineRenderer.updateLivePreviewLine(null, null)
                        if (uiState.stepState == MeasureStepState.Live && uiState.placedPoints.isNotEmpty()) {
                            lineRenderer.updateRectangleWireframe(
                                cornerA = uiState.placedPoints.first(),
                                cornerB = hitUpdate.hitPoint,
                                isPreview = true
                            )
                        } else if (uiState.stepState == MeasureStepState.Done && uiState.placedPoints.size >= 2) {
                            lineRenderer.updateRectangleWireframe(
                                cornerA = uiState.placedPoints[0],
                                cornerB = uiState.placedPoints[1],
                                isPreview = false
                            )
                        } else {
                            lineRenderer.clearBoxWireframe()
                        }
                    }
                    else -> {
                        lineRenderer.clearBoxWireframe()
                        if (uiState.stepState == MeasureStepState.Live && uiState.placedPoints.isNotEmpty()) {
                            lineRenderer.updateLivePreviewLine(
                                start = uiState.placedPoints.last(),
                                end = hitUpdate.hitPoint
                            )
                        } else {
                            lineRenderer.updateLivePreviewLine(null, null)
                        }
                    }
                }

                // Project 3D segments to 2D screen coordinates for crisp floating badges
                val camera = frame.camera
                val currentUnit = settings.unit
                projectedSegments = uiState.segments.mapNotNull { seg ->
                    val pStart = lineRenderer.projectWorldToScreen(
                        camera = camera,
                        viewportWidth = viewportSize.width,
                        viewportHeight = viewportSize.height,
                        point = seg.start
                    ) ?: return@mapNotNull null

                    val pEnd = lineRenderer.projectWorldToScreen(
                        camera = camera,
                        viewportWidth = viewportSize.width,
                        viewportHeight = viewportSize.height,
                        point = seg.end
                    ) ?: return@mapNotNull null

                    OverlaySegment2D(
                        startX = pStart.first,
                        startY = pStart.second,
                        endX = pEnd.first,
                        endY = pEnd.second,
                        distanceMeters = seg.distanceMeters,
                        label = UnitConverter.formatDistance(seg.distanceMeters, currentUnit),
                        isPreview = seg.isPreview
                    )
                }

                // Generate subtle dot grid over detected planes when enabled
                if (settings.showSurfaceGrid && camera.trackingState == TrackingState.TRACKING) {
                    val dots = mutableListOf<Pair<Float, Float>>()
                    val planes = session.getAllTrackables(Plane::class.java)
                    for (plane in planes) {
                        if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
                        val center = plane.centerPose
                        val step = 0.18f
                        val extX = (plane.extentX * 0.45f).coerceAtMost(0.9f)
                        val extZ = (plane.extentZ * 0.45f).coerceAtMost(0.9f)
                        var x = -extX
                        while (x <= extX) {
                            var z = -extZ
                            while (z <= extZ) {
                                val worldPt = center.transformPoint(floatArrayOf(x, 0f, z))
                                val screenPt = lineRenderer.projectWorldToScreen(
                                    camera = camera,
                                    viewportWidth = viewportSize.width,
                                    viewportHeight = viewportSize.height,
                                    point = Point3D(worldPt[0], worldPt[1], worldPt[2])
                                )
                                if (screenPt != null) {
                                    dots.add(screenPt)
                                }
                                z += step
                            }
                            x += step
                        }
                    }
                    detectedSurfaceDots = dots
                } else {
                    detectedSurfaceDots = emptyList()
                }
            }
        )

        // 2. Subtle Surface Dot Grid & 2D Crisp Line Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle iOS-style surface dot grid
            for (dot in detectedSurfaceDots) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(dot.first, dot.second)
                )
            }

            // Projected measurement lines and anchor dots
            for (seg in projectedSegments) {
                drawLine(
                    color = if (seg.isPreview) ArGreen else ArYellow,
                    start = Offset(seg.startX, seg.startY),
                    end = Offset(seg.endX, seg.endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = if (seg.isPreview) {
                        PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    } else null
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(seg.startX, seg.startY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(seg.endX, seg.endY)
                )
            }
        }

        // 3. Floating Live Distance Labels anchored to each 3D segment midpoint
        val density = LocalDensity.current
        projectedSegments.forEach { seg ->
            val midX = ((seg.startX + seg.endX) * 0.5f).roundToInt()
            val midY = ((seg.startY + seg.endY) * 0.5f).roundToInt()
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = midX - with(density) { 46.dp.roundToPx() },
                            y = midY - with(density) { 36.dp.roundToPx() }
                        )
                    }
            ) {
                DistanceBadgePill(
                    text = seg.label,
                    isLive = seg.isPreview
                )
            }
        }

        // 4. Center Reticle (Turns white -> green and animates on plane lock)
        if (uiState.stepState != MeasureStepState.Done) {
            AnimatedCenterReticle(
                isLockedOnSurface = uiState.isReticleLocked,
                isPointReady = uiState.reticlePoint != null,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 5. Top Bar: Unit Chip, Gyro Level Pill, Flashlight, Focus Lock, History & Settings Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit Quick-Toggle Pill
                GlassmorphicCard(
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier
                        .clickable {
                            if (settings.hapticFeedbackEnabled) {
                                localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            viewModel.cycleUnit()
                        }
                        .semantics {
                            contentDescription = "Current unit ${settings.unit.label}. Tap to switch unit."
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Straighten,
                            contentDescription = null,
                            tint = ArYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = settings.unit.symbol.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Gyro Level Indicator Pill (iOS Measure Level)
                val isLevel = levelData.isLevel
                GlassmorphicCard(
                    cornerRadius = 50.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLevel) ArGreen else Color.White.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLevel) "0° LEVEL" else "${levelData.inclinationDegrees}°",
                            color = if (isLevel) ArGreen else Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Action Buttons: Flashlight, Focus Lock, History, Settings
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Flashlight / Torch toggle button
                    FilledTonalIconButton(
                        onClick = {
                            val nextTorch = !uiState.isTorchEnabled
                            activeSession?.let { arMeasureSession.setTorchMode(it, nextTorch) }
                            viewModel.setTorchEnabled(nextTorch)
                            if (settings.hapticFeedbackEnabled) {
                                localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (uiState.isTorchEnabled) ArYellow else Color(0xAA16161A),
                            contentColor = if (uiState.isTorchEnabled) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .semantics {
                                contentDescription = if (uiState.isTorchEnabled) "Turn off flashlight" else "Turn on flashlight"
                            }
                    ) {
                        Icon(
                            imageVector = if (uiState.isTorchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Focus Lock toggle button
                    FilledTonalIconButton(
                        onClick = {
                            val nextLock = !uiState.isFocusLocked
                            activeSession?.let { arMeasureSession.setFocusLock(it, nextLock) }
                            viewModel.setFocusLocked(nextLock)
                            if (settings.hapticFeedbackEnabled) {
                                localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (uiState.isFocusLocked) ArYellow else Color(0xAA16161A),
                            contentColor = if (uiState.isFocusLocked) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .semantics {
                                contentDescription = if (uiState.isFocusLocked) "Unlock focus" else "Lock camera focus"
                            }
                    ) {
                        Icon(
                            imageVector = if (uiState.isFocusLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onOpenHistory,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xAA16161A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .semantics { contentDescription = "Open measurement history" }
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = { showSettingsSheet = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xAA16161A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .semantics { contentDescription = "Open settings" }
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Switcher Pills (Horizontal Scrollable)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC16161A))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MeasureMode.entries.forEach { mode ->
                    val selected = uiState.mode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) ArYellow else Color.Transparent)
                            .clickable {
                                if (settings.hapticFeedbackEnabled) {
                                    localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                lineRenderer.clearAll()
                                viewModel.setMeasureMode(mode)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            StatusInstructionPill(
                text = uiState.instructionText,
                isWarning = uiState.trackingHint != TrackingQualityHint.NONE &&
                    uiState.stepState != MeasureStepState.Done
            )

            // Live Total Banner & Multi-Dimension Display
            AnimatedVisibility(
                visible = uiState.stepState == MeasureStepState.Live,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassmorphicCard(
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (uiState.mode) {
                                MeasureMode.BOX_3D -> {
                                    val b = uiState.boxDimensions
                                    if (b != null) {
                                        Text(
                                            text = "Vol: ${UnitConverter.formatVolume(b.volume, settings.unit)}",
                                            style = TabularMeasureTextStyle.copy(
                                                color = ArYellow,
                                                fontSize = 22.sp
                                            )
                                        )
                                        Text(
                                            text = "L: ${UnitConverter.formatDistance(b.length, settings.unit)}  •  W: ${UnitConverter.formatDistance(b.breadth, settings.unit)}  •  H: ${UnitConverter.formatDistance(b.height, settings.unit)}",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "3D Box Frame",
                                            style = TabularMeasureTextStyle.copy(color = ArYellow, fontSize = 20.sp)
                                        )
                                    }
                                }
                                MeasureMode.RECTANGLE -> {
                                    val r = uiState.rectangleDimensions
                                    if (r != null) {
                                        Text(
                                            text = "Area: ${UnitConverter.formatArea(r.area, settings.unit)}",
                                            style = TabularMeasureTextStyle.copy(
                                                color = ArYellow,
                                                fontSize = 22.sp
                                            )
                                        )
                                        Text(
                                            text = "L: ${UnitConverter.formatDistance(r.length, settings.unit)}  •  W: ${UnitConverter.formatDistance(r.breadth, settings.unit)}",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Surface Frame",
                                            style = TabularMeasureTextStyle.copy(color = ArYellow, fontSize = 20.sp)
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = UnitConverter.formatDistance(
                                            uiState.activeDisplayMeters,
                                            settings.unit
                                        ),
                                        style = TabularMeasureTextStyle.copy(
                                            color = ArYellow,
                                            fontSize = 24.sp
                                        )
                                    )
                                    if (uiState.polygonAreaSqMeters != null) {
                                        Text(
                                            text = "Area: ${
                                                UnitConverter.formatArea(
                                                    uiState.polygonAreaSqMeters!!,
                                                    settings.unit
                                                )
                                            }",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Bottom Controls: Either Shutter + Undo/Clear OR Locked Result Card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Active Measuring Controls
            AnimatedVisibility(
                visible = uiState.stepState != MeasureStepState.Done,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo Button
                    FilledTonalIconButton(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) {
                                localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            lineRenderer.undoLastPoint()
                            viewModel.undoLastPoint()
                        },
                        enabled = uiState.placedPoints.isNotEmpty(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xCC1E1E24),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0x551E1E24),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .semantics { contentDescription = "Undo last point" }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                    }

                    // Center "+" Shutter Button
                    ShutterAddPointButton(
                        enabled = uiState.reticlePoint != null,
                        onClick = {
                            val pointToAnchor = viewModel.onAddPointClicked()
                            if (pointToAnchor != null) {
                                if (settings.hapticFeedbackEnabled) {
                                    localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                activeSession?.let { session ->
                                    lineRenderer.placeAnchorPoint(session, pointToAnchor)
                                }
                            }
                        }
                    )

                    // Right Action: Finish Chain (in Multi-Point mode with 2+ points) or Clear All
                    if (uiState.mode == MeasureMode.MULTI_POINT && uiState.placedPoints.size >= 2) {
                        FilledTonalIconButton(
                            onClick = {
                                if (settings.hapticFeedbackEnabled) {
                                    localView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }
                                lineRenderer.updateLivePreviewLine(null, null)
                                viewModel.finishMultiPointMeasurement()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = ArGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .semantics { contentDescription = "Finish multi-point measurement" }
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                if (settings.hapticFeedbackEnabled) {
                                    localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                                lineRenderer.clearAll()
                                viewModel.clearAll()
                            },
                            enabled = uiState.placedPoints.isNotEmpty(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xCC1E1E24),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0x551E1E24),
                                disabledContentColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .semantics { contentDescription = "Clear all points" }
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        }
                    }
                }
            }

            // Locked Measurement Result Card (State == Done)
            AnimatedVisibility(
                visible = uiState.stepState == MeasureStepState.Done,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.fillMaxWidth()
            ) {
                val formattedDistance = UnitConverter.formatDistance(
                    uiState.committedTotalMeters,
                    settings.unit
                )
                val formattedArea = uiState.polygonAreaSqMeters?.let {
                    UnitConverter.formatArea(it, settings.unit)
                }

                GlassmorphicCard(
                    cornerRadius = 28.dp,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.mode.label.uppercase(),
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                when (uiState.mode) {
                                    MeasureMode.BOX_3D -> {
                                        val b = uiState.boxDimensions
                                        if (b != null) {
                                            Text(
                                                text = UnitConverter.formatVolume(b.volume, settings.unit),
                                                style = TabularMeasureTextStyle.copy(
                                                    color = ArYellow,
                                                    fontSize = 32.sp
                                                )
                                            )
                                            Text(
                                                text = "L: ${UnitConverter.formatDistance(b.length, settings.unit)}  •  W: ${UnitConverter.formatDistance(b.breadth, settings.unit)}  •  H: ${UnitConverter.formatDistance(b.height, settings.unit)}",
                                                style = TabularMeasureTextStyle.copy(
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 14.sp
                                                )
                                            )
                                        }
                                    }
                                    MeasureMode.RECTANGLE -> {
                                        val r = uiState.rectangleDimensions
                                        if (r != null) {
                                            Text(
                                                text = UnitConverter.formatArea(r.area, settings.unit),
                                                style = TabularMeasureTextStyle.copy(
                                                    color = ArYellow,
                                                    fontSize = 32.sp
                                                )
                                            )
                                            Text(
                                                text = "L: ${UnitConverter.formatDistance(r.length, settings.unit)}  •  W: ${UnitConverter.formatDistance(r.breadth, settings.unit)}",
                                                style = TabularMeasureTextStyle.copy(
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 14.sp
                                                )
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = formattedDistance,
                                            style = TabularMeasureTextStyle.copy(
                                                color = ArYellow,
                                                fontSize = 34.sp
                                            )
                                        )
                                        if (formattedArea != null) {
                                            Text(
                                                text = "Enclosed Area: $formattedArea",
                                                style = TabularMeasureTextStyle.copy(
                                                    color = ArGreen,
                                                    fontSize = 16.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Secondary Metric Conversion Preview
                            val altUnit = if (settings.unit == com.example.measurear.util.MeasureUnit.CM ||
                                settings.unit == com.example.measurear.util.MeasureUnit.M
                            ) {
                                com.example.measurear.util.MeasureUnit.FT
                            } else {
                                com.example.measurear.util.MeasureUnit.CM
                            }
                            Text(
                                text = UnitConverter.formatDistance(
                                    uiState.committedTotalMeters,
                                    altUnit
                                ),
                                style = TabularMeasureTextStyle.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 18.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        val measurementSummary = when (uiState.mode) {
                            MeasureMode.BOX_3D -> {
                                val b = uiState.boxDimensions
                                if (b != null) {
                                    "${UnitConverter.formatVolume(b.volume, settings.unit)} (L: ${UnitConverter.formatDistance(b.length, settings.unit)} W: ${UnitConverter.formatDistance(b.breadth, settings.unit)} H: ${UnitConverter.formatDistance(b.height, settings.unit)})"
                                } else formattedDistance
                            }
                            MeasureMode.RECTANGLE -> {
                                val r = uiState.rectangleDimensions
                                if (r != null) {
                                    "${UnitConverter.formatArea(r.area, settings.unit)} (L: ${UnitConverter.formatDistance(r.length, settings.unit)} W: ${UnitConverter.formatDistance(r.breadth, settings.unit)})"
                                } else formattedDistance
                            }
                            else -> {
                                if (formattedArea != null) "$formattedDistance ($formattedArea)" else formattedDistance
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Save Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        val surface = arSceneViewRef
                                        val bmp = if (surface != null) {
                                            BitmapShareHelper.captureArFrameWithOverlay(
                                                surfaceView = surface,
                                                segments = projectedSegments,
                                                summaryText = measurementSummary
                                            )
                                        } else null
                                        val thumbPath = if (bmp != null) {
                                            BitmapShareHelper.saveThumbnailToInternalStorage(context, bmp)
                                        } else null
                                        viewModel.saveCurrentMeasurement(thumbPath)
                                        snackbarHostState.showSnackbar("Measurement saved to History")
                                    }
                                },
                                enabled = !uiState.isSavedToHistory,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArYellow,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSavedToHistory) {
                                        Icons.Rounded.Check
                                    } else {
                                        Icons.Rounded.BookmarkAdd
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isSavedToHistory) "Saved" else "Save",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Share Screenshot Button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val surface = arSceneViewRef ?: return@launch
                                        val summary = "Measure AR: $measurementSummary"
                                        val bitmap = BitmapShareHelper.captureArFrameWithOverlay(
                                            surfaceView = surface,
                                            segments = projectedSegments,
                                            summaryText = summary
                                        )
                                        if (bitmap != null) {
                                            BitmapShareHelper.shareBitmap(context, bitmap, summary)
                                        } else {
                                            snackbarHostState.showSnackbar("Could not capture AR frame")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Undo Last Point
                            OutlinedButton(
                                onClick = {
                                    lineRenderer.undoLastPoint()
                                    viewModel.undoLastPoint()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Undo", color = Color.White)
                            }

                            // New Measurement
                            Button(
                                onClick = {
                                    lineRenderer.clearAll()
                                    viewModel.clearAll()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New")
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp)
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            settings = settings,
            onUnitSelected = viewModel::setUnit,
            onToggleGrid = viewModel::setShowSurfaceGrid,
            onToggleHaptics = viewModel::setHapticFeedback,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun PermissionRequiredFallback(
    isPermanent: Boolean,
    onRequestAgain: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassmorphicCard(
            cornerRadius = 28.dp,
            contentPadding = PaddingValues(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ArYellow.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = ArYellow,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Measure AR needs camera access to detect real-world surfaces and calculate 3D distances in augmented reality.",
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = if (isPermanent) onOpenSettings else onRequestAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArYellow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPermanent) "Open App Settings" else "Grant Camera Permission",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ArUnsupportedFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassmorphicCard(
            cornerRadius = 28.dp,
            contentPadding = PaddingValues(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ARCore Not Supported",
                    style = MaterialTheme.typography.titleLarge,
                    color = ArYellow
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This device does not support Google Play Services for AR (ARCore), which is required for 3D depth and surface tracking.",
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
