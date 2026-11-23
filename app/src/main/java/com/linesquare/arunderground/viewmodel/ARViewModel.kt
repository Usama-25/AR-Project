package com.linesquare.arunderground.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linesquare.arunderground.ar.DeviceOrientation
import com.linesquare.arunderground.ar.PipeGeometryBuilder
import com.linesquare.arunderground.ar.PipeSegment3D
import com.linesquare.arunderground.gis.BuildingRepository
import com.linesquare.arunderground.gis.SampleDataRepository
import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.BuildingCategory
import com.linesquare.arunderground.model.BuildingComponent
import com.linesquare.arunderground.model.BuildingPreset
import com.linesquare.arunderground.model.GeoPoint
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.model.UtilityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RenderMode {
    SIMULATION,       // Indoor Horizon simulation canvas
    LIVE_CAMERA,      // CameraX live video feed with interactive 3D dynamic AR component overlay
    ARCORE_NATIVE     // Google ARCore + Filament 3D engine (for certified devices)
}

/**
 * UI State container for the Augmented Reality Underground Utility & Building Component viewer.
 */
data class ARUiState(
    val origin: GeoPoint = SampleDataRepository.DEFAULT_ORIGIN,
    val currentPreset: BuildingPreset = BuildingPreset.COMMERCIAL_BUILDING,
    val allComponents: List<BuildingComponent> = BuildingRepository.getCommercialBuildingComponents(),
    val enabledCategories: Set<BuildingCategory> = BuildingCategory.values().toSet(),
    val selectedComponent: BuildingComponent? = null,
    val showDimensionBadges: Boolean = true,
    val showDimensionLines: Boolean = true,
    val deviceOrientation: DeviceOrientation = DeviceOrientation(),
    val allPipes: List<UtilityPipe> = SampleDataRepository.getSamplePipes(),
    val enabledTypes: Set<UtilityType> = UtilityType.values().toSet(),
    val selectedPipe: UtilityPipe? = null,
    val alignmentOffset: AlignmentOffset = AlignmentOffset(),
    val isCalibrationOpen: Boolean = false,
    val isSimulationMode: Boolean = false,
    val renderMode: RenderMode = RenderMode.LIVE_CAMERA,
    val isArCoreAvailable: Boolean = false,
    val trackingStatus: String = "Dynamic 60 FPS Sensor AR Active",
    val segments: List<PipeSegment3D> = emptyList()
) {
    val visibleComponents: List<BuildingComponent>
        get() = allComponents.filter { enabledCategories.contains(it.category) }
}

class ARViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ARUiState())
    val uiState: StateFlow<ARUiState> = _uiState.asStateFlow()

    init {
        // Immediate in-memory startup
        recomputeSegments()
    }

    /**
     * Switches the active building environment preset (Commercial MEP, Aslam Market, Utilities, Industrial Plant).
     */
    fun setPreset(preset: BuildingPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val components = BuildingRepository.getPresetComponents(preset, getApplication())
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            currentPreset = preset,
                            allComponents = components,
                            selectedComponent = null,
                            selectedPipe = null
                        )
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Toggles visibility of a specific building component category.
     */
    fun toggleCategory(category: BuildingCategory) {
        _uiState.update { current ->
            val updated = if (current.enabledCategories.contains(category)) {
                current.enabledCategories - category
            } else {
                current.enabledCategories + category
            }
            current.copy(enabledCategories = updated)
        }
    }

    /**
     * Selects a building component for detailed engineering dimension inspection.
     */
    fun selectComponent(component: BuildingComponent?) {
        _uiState.update { current ->
            current.copy(
                selectedComponent = component,
                selectedPipe = component?.let { comp ->
                    current.allPipes.firstOrNull { it.id == comp.id }
                }
            )
        }
    }

    /**
     * Toggles 3D floating dimension badges in the live camera AR feed.
     */
    fun toggleDimensionBadges() {
        _uiState.update { it.copy(showDimensionBadges = !it.showDimensionBadges) }
    }

    /**
     * Toggles CAD dimension arrows and extension lines.
     */
    fun toggleDimensionLines() {
        _uiState.update { it.copy(showDimensionLines = !it.showDimensionLines) }
    }

    /**
     * Updates real-time device orientation angles and matrix from sensor fusion.
     */
    fun updateOrientation(orientation: DeviceOrientation) {
        _uiState.update { it.copy(deviceOrientation = orientation) }
    }

    /**
     * Toggles visibility of a specific utility category (legacy compatibility).
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
     * Selects a pipeline asset for inspection (legacy compatibility).
     */
    fun selectPipe(pipe: UtilityPipe?) {
        _uiState.update { current ->
            current.copy(
                selectedPipe = pipe,
                selectedComponent = pipe?.let { p ->
                    current.allComponents.firstOrNull { it.id == p.id }
                }
            )
        }
    }

    /**
     * Nudges the entire 3D building network in AR space to compensate for GPS drift
     * or align with physical walls/columns.
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
     * Rotates the 3D building network around the vertical axis by the specified degrees.
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
     * Tilts the 3D building network up or down by the specified pitch degrees.
     */
    fun rotatePitch(degrees: Float) {
        _uiState.update { current ->
            val cur = current.alignmentOffset
            current.copy(
                alignmentOffset = cur.copy(
                    pitchOffsetDeg = cur.pitchOffsetDeg + degrees
                )
            )
        }
        recomputeSegments()
    }

    /**
     * Resets manual calibration offsets to zero.
     */
    fun resetAlignment() {
        _uiState.update { it.copy(alignmentOffset = AlignmentOffset()) }
        recomputeSegments()
    }

    /**
     * Toggles visibility of the manual calibration panel.
     */
    fun toggleCalibrationPanel() {
        _uiState.update { it.copy(isCalibrationOpen = !it.isCalibrationOpen) }
    }

    /**
     * Sets explicit render mode.
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
     * Updates whether ARCore is installed and ready.
     */
    fun setArCoreAvailable(available: Boolean) {
        _uiState.update { it.copy(isArCoreAvailable = available) }
    }

    /**
     * Toggles between indoor test simulation mode and live camera AR mode.
     */
    fun toggleSimulationMode() {
        _uiState.update { current ->
            if (current.renderMode == RenderMode.SIMULATION) {
                val nextMode = if (current.isArCoreAvailable) RenderMode.ARCORE_NATIVE else RenderMode.LIVE_CAMERA
                current.copy(renderMode = nextMode, isSimulationMode = false)
            } else {
                current.copy(renderMode = RenderMode.SIMULATION, isSimulationMode = true)
            }
        }
    }

    /**
     * Updates reference datum when GPS coordinates arrive.
     */
    fun updateGpsOrigin(newOrigin: GeoPoint) {
        _uiState.update { it.copy(origin = newOrigin) }
        recomputeSegments()
    }

    /**
     * Updates the diagnostic tracking status text.
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
