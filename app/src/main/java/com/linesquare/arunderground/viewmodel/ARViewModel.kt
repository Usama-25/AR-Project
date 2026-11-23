package com.linesquare.arunderground.viewmodel

import androidx.lifecycle.ViewModel
import com.linesquare.arunderground.ar.PipeGeometryBuilder
import com.linesquare.arunderground.ar.PipeSegment3D
import com.linesquare.arunderground.gis.SampleDataRepository
import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.GeoPoint
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.model.UtilityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RenderMode {
    SIMULATION,       // Indoor Horizon simulation canvas
    LIVE_CAMERA,      // CameraX live video feed with interactive 3D pipe overlay
    ARCORE_NATIVE     // Google ARCore + Filament 3D engine (for certified devices)
}

/**
 * UI State container for the Augmented Reality Underground Utility viewer.
 */
data class ARUiState(
    val origin: GeoPoint = SampleDataRepository.DEFAULT_ORIGIN,
    val allPipes: List<UtilityPipe> = SampleDataRepository.getSamplePipes(),
    val enabledTypes: Set<UtilityType> = UtilityType.values().toSet(),
    val selectedPipe: UtilityPipe? = null,
    val alignmentOffset: AlignmentOffset = AlignmentOffset(),
    val isCalibrationOpen: Boolean = false,
    val isSimulationMode: Boolean = true,
    val renderMode: RenderMode = RenderMode.SIMULATION,
    val isArCoreAvailable: Boolean = false,
    val trackingStatus: String = "Ready (Live Camera + Simulation Available)",
    val segments: List<PipeSegment3D> = emptyList()
)

class ARViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ARUiState())
    val uiState: StateFlow<ARUiState> = _uiState.asStateFlow()

    init {
        recomputeSegments()
    }

    /**
     * Toggles visibility of a specific utility category (e.g. Water, Sewer, Gas, Sanitary)
     */
    fun toggleLayer(type: UtilityType) {
        _uiState.update { current ->
            val updated = if (current.enabledTypes.contains(type)) {
                current.enabledTypes - type
            } else {
                current.enabledTypes + type
            }
            current.copy(enabledTypes = updated)
        }
        recomputeSegments()
    }

    /**
     * Selects a pipeline asset for inspection
     */
    fun selectPipe(pipe: UtilityPipe?) {
        _uiState.update { it.copy(selectedPipe = pipe) }
    }

    /**
     * Nudges the entire 3D pipeline network in AR space to compensate for GPS drift
     */
    fun nudge(dx: Float, dy: Float, dz: Float) {
        _uiState.update { current ->
            val cur = current.alignmentOffset
            current.copy(
                alignmentOffset = cur.copy(
                    offsetX = cur.offsetX + dx,
                    offsetY = cur.offsetY + dy,
                    offsetZ = cur.offsetZ + dz
                )
            )
        }
        recomputeSegments()
    }

    /**
     * Rotates the 3D pipeline network around the vertical axis by the specified degrees
     */
    fun rotateYaw(degrees: Float) {
        _uiState.update { current ->
            val cur = current.alignmentOffset
            current.copy(
                alignmentOffset = cur.copy(
                    rotationYawDeg = cur.rotationYawDeg + degrees
                )
            )
        }
        recomputeSegments()
    }

    /**
     * Resets manual calibration offsets to zero
     */
    fun resetAlignment() {
        _uiState.update { it.copy(alignmentOffset = AlignmentOffset()) }
        recomputeSegments()
    }

    /**
     * Toggles visibility of the manual calibration panel
     */
    fun toggleCalibrationPanel() {
        _uiState.update { it.copy(isCalibrationOpen = !it.isCalibrationOpen) }
    }

    /**
     * Sets explicit render mode
     */
    fun setRenderMode(mode: RenderMode) {
        _uiState.update {
            it.copy(
                renderMode = mode,
                isSimulationMode = (mode == RenderMode.SIMULATION)
            )
        }
    }

    /**
     * Updates whether ARCore is installed and ready
     */
    fun setArCoreAvailable(available: Boolean) {
        _uiState.update { it.copy(isArCoreAvailable = available) }
    }

    /**
     * Toggles between indoor test simulation mode and live camera AR mode
     */
    fun toggleSimulationMode() {
        _uiState.update { current ->
            if (current.renderMode == RenderMode.SIMULATION) {
                // If user clicks camera icon: activate ARCore if supported, otherwise Live Camera
                val nextMode = if (current.isArCoreAvailable) RenderMode.ARCORE_NATIVE else RenderMode.LIVE_CAMERA
                current.copy(renderMode = nextMode, isSimulationMode = false)
            } else {
                current.copy(renderMode = RenderMode.SIMULATION, isSimulationMode = true)
            }
        }
    }

    /**
     * Updates reference datum when GPS coordinates arrive from Android LocationManager
     */
    fun updateGpsOrigin(newOrigin: GeoPoint) {
        _uiState.update { it.copy(origin = newOrigin) }
        recomputeSegments()
    }

    /**
     * Updates the diagnostic tracking status text
     */
    fun updateTrackingStatus(status: String) {
        _uiState.update { it.copy(trackingStatus = status) }
    }

    private fun recomputeSegments() {
        val current = _uiState.value
        val visiblePipes = current.allPipes.filter { current.enabledTypes.contains(it.utilityType) }
        val newSegments = PipeGeometryBuilder.buildSegments(
            pipes = visiblePipes,
            origin = current.origin,
            offset = current.alignmentOffset
        )
        _uiState.update { it.copy(segments = newSegments) }
    }
}
