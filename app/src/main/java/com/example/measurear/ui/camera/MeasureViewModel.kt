package com.example.measurear.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.measurear.ar.BoxDimensions
import com.example.measurear.ar.DistanceCalculator
import com.example.measurear.ar.MeasureMode
import com.example.measurear.ar.Point3D
import com.example.measurear.ar.RectangleDimensions
import com.example.measurear.ar.TrackingQualityHint
import com.example.measurear.data.MeasurementEntity
import com.example.measurear.data.MeasurementRepository
import com.example.measurear.data.SettingsRepository
import com.example.measurear.data.UserSettings
import com.example.measurear.util.MeasureUnit
import com.example.measurear.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MeasureStepState {
    Searching,
    ReadyToPlaceA,
    PlacedA,
    Live,
    Done
}

data class SegmentMeasurement(
    val start: Point3D,
    val end: Point3D,
    val distanceMeters: Float,
    val isPreview: Boolean
)

data class MeasureUiState(
    val stepState: MeasureStepState = MeasureStepState.Searching,
    val mode: MeasureMode = MeasureMode.STANDARD,
    val placedPoints: List<Point3D> = emptyList(),
    val reticlePoint: Point3D? = null,
    val isReticleLocked: Boolean = false,
    val isEdgeSnapped: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isFocusLocked: Boolean = false,
    val trackingHint: TrackingQualityHint = TrackingQualityHint.INITIALIZING,
    val liveSegmentMeters: Float = 0f,
    val committedTotalMeters: Float = 0f,
    val polygonAreaSqMeters: Float? = null,
    val boxDimensions: BoxDimensions? = null,
    val rectangleDimensions: RectangleDimensions? = null,
    val isSavedToHistory: Boolean = false
) {
    val activeDisplayMeters: Float
        get() = when (stepState) {
            MeasureStepState.Live -> committedTotalMeters + liveSegmentMeters
            MeasureStepState.Done -> committedTotalMeters
            else -> 0f
        }

    val segments: List<SegmentMeasurement>
        get() {
            val list = mutableListOf<SegmentMeasurement>()
            for (i in 0 until placedPoints.size - 1) {
                val a = placedPoints[i]
                val b = placedPoints[i + 1]
                val dist = if (mode == MeasureMode.HEIGHT) {
                    DistanceCalculator.calculateVerticalHeight(a, b)
                } else {
                    DistanceCalculator.calculateEuclideanDistance(a, b)
                }
                list.add(SegmentMeasurement(a, b, dist, isPreview = false))
            }
            if (stepState == MeasureStepState.Live && placedPoints.isNotEmpty() && reticlePoint != null) {
                val last = placedPoints.last()
                val current = reticlePoint
                val dist = if (mode == MeasureMode.HEIGHT) {
                    DistanceCalculator.calculateVerticalHeight(last, current)
                } else {
                    DistanceCalculator.calculateEuclideanDistance(last, current)
                }
                list.add(SegmentMeasurement(last, current, dist, isPreview = true))
            }
            return list
        }

    val instructionText: String
        get() {
            if (trackingHint != TrackingQualityHint.NONE && stepState != MeasureStepState.Done) {
                return trackingHint.message ?: "Move phone slowly"
            }
            return when (stepState) {
                MeasureStepState.Searching -> "Move phone slowly to detect surfaces"
                MeasureStepState.ReadyToPlaceA -> when (mode) {
                    MeasureMode.STANDARD -> "Tap + to place start point"
                    MeasureMode.MULTI_POINT -> "Tap + to start perimeter chain"
                    MeasureMode.HEIGHT -> "Aim at floor and tap + to set base"
                    MeasureMode.BOX_3D -> "Tap + at 1st box corner"
                    MeasureMode.RECTANGLE -> "Tap + at 1st surface corner"
                }
                MeasureStepState.PlacedA -> "Move slightly to start measuring"
                MeasureStepState.Live -> when (mode) {
                    MeasureMode.STANDARD -> "Move to target and tap + to finish"
                    MeasureMode.MULTI_POINT -> "Tap + to add point, or checkmark to finish"
                    MeasureMode.HEIGHT -> "Tilt phone up to top edge and tap +"
                    MeasureMode.BOX_3D -> "Move to opposite diagonal corner and tap +"
                    MeasureMode.RECTANGLE -> "Move diagonally across surface and tap +"
                }
                MeasureStepState.Done -> when (mode) {
                    MeasureMode.BOX_3D -> "3D Box dimensions locked"
                    MeasureMode.RECTANGLE -> "Surface dimensions locked"
                    else -> "Measurement locked"
                }
            }
        }
}

@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val historyList: StateFlow<List<MeasurementEntity>> =
        measurementRepository.allMeasurements.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onFrameHitUpdated(
        hitPoint: Point3D?,
        isPlaneLocked: Boolean,
        isEdgeSnapped: Boolean,
        trackingHint: TrackingQualityHint
    ) {
        _uiState.update { current ->
            if (current.stepState == MeasureStepState.Done) {
                return@update current.copy(
                    reticlePoint = hitPoint,
                    isReticleLocked = isPlaneLocked,
                    isEdgeSnapped = isEdgeSnapped,
                    trackingHint = trackingHint
                )
            }

            val nextStep = when {
                current.placedPoints.isEmpty() -> {
                    if (hitPoint != null) MeasureStepState.ReadyToPlaceA else MeasureStepState.Searching
                }
                hitPoint == null -> MeasureStepState.PlacedA
                else -> {
                    val lastPoint = current.placedPoints.last()
                    val delta = DistanceCalculator.calculateEuclideanDistance(lastPoint, hitPoint)
                    if (delta > 0.005f || current.stepState == MeasureStepState.Live) {
                        MeasureStepState.Live
                    } else {
                        MeasureStepState.PlacedA
                    }
                }
            }

            val liveDistance = if (current.placedPoints.isNotEmpty() && hitPoint != null) {
                val last = current.placedPoints.last()
                if (current.mode == MeasureMode.HEIGHT) {
                    DistanceCalculator.calculateVerticalHeight(last, hitPoint)
                } else {
                    DistanceCalculator.calculateEuclideanDistance(last, hitPoint)
                }
            } else {
                0f
            }

            val liveBox = if (current.mode == MeasureMode.BOX_3D && current.placedPoints.isNotEmpty() && hitPoint != null) {
                DistanceCalculator.calculateBoundingBox(current.placedPoints.first(), hitPoint)
            } else null

            val liveRect = if (current.mode == MeasureMode.RECTANGLE && current.placedPoints.isNotEmpty() && hitPoint != null) {
                DistanceCalculator.calculateRectangleDimensions(current.placedPoints.first(), hitPoint)
            } else null

            current.copy(
                stepState = nextStep,
                reticlePoint = hitPoint,
                isReticleLocked = isPlaneLocked,
                isEdgeSnapped = isEdgeSnapped,
                trackingHint = trackingHint,
                liveSegmentMeters = liveDistance,
                boxDimensions = liveBox ?: current.boxDimensions,
                rectangleDimensions = liveRect ?: current.rectangleDimensions
            )
        }
    }

    /**
     * Triggered when the user taps the "+" shutter button.
     * Returns the 3D coordinate to anchor if a point should be placed in the 3D scene.
     */
    fun onAddPointClicked(): Point3D? {
        val state = _uiState.value
        val targetPoint = state.reticlePoint ?: return null
        if (state.stepState == MeasureStepState.Done || state.stepState == MeasureStepState.Searching) {
            return null
        }

        val updatedPoints = state.placedPoints + targetPoint
        recalculateStateForPoints(updatedPoints, state.mode, forceFinish = false)
        return targetPoint
    }

    fun finishMultiPointMeasurement() {
        val state = _uiState.value
        if (state.placedPoints.size >= 2) {
            recalculateStateForPoints(state.placedPoints, state.mode, forceFinish = true)
        }
    }

    fun undoLastPoint() {
        val state = _uiState.value
        if (state.placedPoints.isEmpty()) return
        val updatedPoints = state.placedPoints.dropLast(1)
        recalculateStateForPoints(updatedPoints, state.mode, forceFinish = false)
    }

    fun clearAll() {
        _uiState.update { current ->
            val nextStep = if (current.reticlePoint != null) {
                MeasureStepState.ReadyToPlaceA
            } else {
                MeasureStepState.Searching
            }
            current.copy(
                stepState = nextStep,
                placedPoints = emptyList(),
                liveSegmentMeters = 0f,
                committedTotalMeters = 0f,
                polygonAreaSqMeters = null,
                boxDimensions = null,
                rectangleDimensions = null,
                isSavedToHistory = false
            )
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isTorchEnabled = enabled) }
    }

    fun setFocusLocked(locked: Boolean) {
        _uiState.update { it.copy(isFocusLocked = locked) }
    }

    fun setMeasureMode(mode: MeasureMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode) }
        clearAll()
    }

    fun cycleUnit() {
        val currentUnit = settings.value.unit
        val units = MeasureUnit.entries
        val nextUnit = units[(currentUnit.ordinal + 1) % units.size]
        setUnit(nextUnit)
    }

    fun setUnit(unit: MeasureUnit) {
        viewModelScope.launch {
            settingsRepository.setMeasureUnit(unit)
        }
    }

    fun setShowSurfaceGrid(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowSurfaceGrid(show)
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(enabled)
        }
    }

    fun saveCurrentMeasurement(thumbnailPath: String?) {
        val state = _uiState.value
        if (state.stepState != MeasureStepState.Done || state.isSavedToHistory) return

        val unit = settings.value.unit
        val formattedPrimary = UnitConverter.formatDistance(state.committedTotalMeters, unit)
        val formattedFull = when (state.mode) {
            MeasureMode.BOX_3D -> {
                val b = state.boxDimensions
                if (b != null) {
                    val lStr = UnitConverter.formatDistance(b.length, unit)
                    val wStr = UnitConverter.formatDistance(b.breadth, unit)
                    val hStr = UnitConverter.formatDistance(b.height, unit)
                    val vStr = UnitConverter.formatVolume(b.volume, unit)
                    "$lStr × $wStr × $hStr ($vStr)"
                } else formattedPrimary
            }
            MeasureMode.RECTANGLE -> {
                val r = state.rectangleDimensions
                if (r != null) {
                    val lStr = UnitConverter.formatDistance(r.length, unit)
                    val wStr = UnitConverter.formatDistance(r.breadth, unit)
                    val aStr = UnitConverter.formatArea(r.area, unit)
                    "$lStr × $wStr ($aStr)"
                } else formattedPrimary
            }
            else -> {
                if (state.polygonAreaSqMeters != null && state.polygonAreaSqMeters > 0f) {
                    "$formattedPrimary • Area: ${UnitConverter.formatArea(state.polygonAreaSqMeters, unit)}"
                } else {
                    formattedPrimary
                }
            }
        }

        val segmentLabels = state.segments
            .filter { !it.isPreview }
            .mapIndexed { idx, seg ->
                "S${idx + 1}: ${UnitConverter.formatDistance(seg.distanceMeters, unit)}"
            }
            .joinToString(" | ")

        viewModelScope.launch {
            measurementRepository.saveMeasurement(
                MeasurementEntity(
                    timestamp = System.currentTimeMillis(),
                    modeName = state.mode.label,
                    primaryMeters = state.committedTotalMeters,
                    areaSquareMeters = state.polygonAreaSqMeters,
                    formattedValue = formattedFull,
                    pointsSummary = segmentLabels.ifEmpty { formattedFull },
                    pointCount = state.placedPoints.size,
                    thumbnailPath = thumbnailPath
                )
            )
            _uiState.update { it.copy(isSavedToHistory = true) }
        }
    }

    fun deleteHistoryItem(entity: MeasurementEntity) {
        viewModelScope.launch {
            measurementRepository.deleteMeasurement(entity)
        }
    }

    private fun recalculateStateForPoints(
        points: List<Point3D>,
        mode: MeasureMode,
        forceFinish: Boolean
    ) {
        if (points.isEmpty()) {
            clearAll()
            return
        }

        val reachedTargetCount = when (mode) {
            MeasureMode.STANDARD, MeasureMode.HEIGHT, MeasureMode.BOX_3D, MeasureMode.RECTANGLE -> points.size >= 2
            MeasureMode.MULTI_POINT -> forceFinish && points.size >= 2
        }

        var boxDims: BoxDimensions? = null
        var rectDims: RectangleDimensions? = null

        val totalMeters = when (mode) {
            MeasureMode.HEIGHT -> {
                if (points.size >= 2) {
                    DistanceCalculator.calculateVerticalHeight(points[0], points[1])
                } else 0f
            }
            MeasureMode.STANDARD -> {
                DistanceCalculator.calculatePolylineLength(points, closeLoop = false)
            }
            MeasureMode.MULTI_POINT -> {
                val closeLoop = reachedTargetCount && points.size >= 4
                DistanceCalculator.calculatePolylineLength(points, closeLoop = closeLoop)
            }
            MeasureMode.BOX_3D -> {
                if (points.size >= 2) {
                    boxDims = DistanceCalculator.calculateBoundingBox(points[0], points[1])
                    boxDims.length
                } else 0f
            }
            MeasureMode.RECTANGLE -> {
                if (points.size >= 2) {
                    rectDims = DistanceCalculator.calculateRectangleDimensions(points[0], points[1])
                    rectDims.perimeter
                } else 0f
            }
        }

        val areaSqMeters = when (mode) {
            MeasureMode.MULTI_POINT -> if (points.size >= 4) DistanceCalculator.calculatePolygonArea(points) else null
            MeasureMode.RECTANGLE -> rectDims?.area
            MeasureMode.BOX_3D -> boxDims?.volume
            else -> null
        }

        val nextStep = when {
            reachedTargetCount -> MeasureStepState.Done
            points.size == 1 -> MeasureStepState.PlacedA
            else -> MeasureStepState.Live
        }

        _uiState.update { current ->
            current.copy(
                stepState = nextStep,
                placedPoints = points,
                committedTotalMeters = totalMeters,
                liveSegmentMeters = 0f,
                polygonAreaSqMeters = areaSqMeters,
                boxDimensions = boxDims ?: current.boxDimensions,
                rectangleDimensions = rectDims ?: current.rectangleDimensions,
                isSavedToHistory = false
            )
        }
    }
}
