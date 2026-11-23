package com.linesquare.arunderground.gis

import android.content.Context
import com.linesquare.arunderground.model.BuildingCategory
import com.linesquare.arunderground.model.BuildingComponent
import com.linesquare.arunderground.model.BuildingPreset
import com.linesquare.arunderground.model.ComponentGeometry
import com.linesquare.arunderground.model.GeoPoint
import com.linesquare.arunderground.model.UtilityPipe
import com.linesquare.arunderground.model.UtilityType

/**
 * Building Information & Infrastructure Repository.
 * Supplies realistic 3D building components with exact engineering dimensions
 * (Columns, Beams, HVAC Ducts, Water Pipes, Cable Trays, Gas Lines, Drainage).
 */
object BuildingRepository {

    /**
     * Returns components for the requested building preset scenario.
     */
    fun getPresetComponents(preset: BuildingPreset, context: Context? = null): List<BuildingComponent> = when (preset) {
        BuildingPreset.ASLAM_MARKET -> {
            if (context != null) {
                val loaded = GeoJsonLoader.loadAslamMarketUtilities(context)
                if (loaded.isNotEmpty()) loaded else getCommercialBuildingComponents()
            } else {
                getCommercialBuildingComponents()
            }
        }
        BuildingPreset.COMMERCIAL_BUILDING -> getCommercialBuildingComponents()
        BuildingPreset.UNDERGROUND_UTILITIES -> getSubsurfaceUtilityComponents()
        BuildingPreset.INDUSTRIAL_PLANT -> getIndustrialPlantComponents()
    }

    /**
     * Scenario: Modern Building Sections (Floor, Walls, Ceiling).
     * Arranged in an authentic room layout situated directly in front of the camera (Z = -2.0m to -2.6m),
     * stacking HVAC duct, electrical tray, water pipe, gas line, drainage, and structural columns.
     */
    fun getCommercialBuildingComponents(): List<BuildingComponent> = listOf(
        // === CEILING / OVERHEAD SECTION (+0.45m to +0.85m) ===
        // HVAC PRIMARY SUPPLY AIR DUCT (Ceiling Section)
        BuildingComponent(
            id = "DUCT-SUP-101",
            name = "Supply Air Duct (HVAC 3D Box)",
            category = BuildingCategory.HVAC,
            geometry = ComponentGeometry.RECTANGULAR_DUCT,
            startPoint = floatArrayOf(-2.8f, 0.75f, -2.5f),
            endPoint = floatArrayOf(2.8f, 0.75f, -2.5f),
            widthMm = 600,
            heightMm = 400,
            lengthM = 5.6f,
            elevationOrDepthM = 2.8f,
            wallThicknessMm = 1.2f,
            material = "Galvanized Steel DW/144",
            systemLevel = "Ceiling Section (+2.8m)",
            ifcType = "IfcDuctSegment",
            installStatus = "Active Airflow"
        ),

        // OVERHEAD CABLE TRAY (Electrical Section)
        BuildingComponent(
            id = "CT-ELEC-401",
            name = "Power Cable Tray (Ladder 3D)",
            category = BuildingCategory.ELECTRICAL,
            geometry = ComponentGeometry.CABLE_TRAY,
            startPoint = floatArrayOf(-2.8f, 0.45f, -2.3f),
            endPoint = floatArrayOf(2.8f, 0.45f, -2.3f),
            widthMm = 400,
            heightMm = 100,
            lengthM = 5.6f,
            elevationOrDepthM = 2.5f,
            wallThicknessMm = 2.0f,
            material = "Perforated Galvanized Steel",
            systemLevel = "Ceiling Section (+2.5m)",
            ifcType = "IfcCableCarrierSegment",
            installStatus = "Cables Active"
        ),

        // === WALL / EYE LEVEL SECTION (+0.10m to -0.30m) ===
        // DOMESTIC POTABLE WATER SUPPLY (Wall Cavity, Eye Level)
        BuildingComponent(
            id = "W-POT-201",
            name = "Potable Cold Water (3D Cylinder Pipe)",
            category = BuildingCategory.PLUMBING,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-2.5f, 0.10f, -2.0f),
            endPoint = floatArrayOf(2.5f, 0.10f, -2.0f),
            diameterMm = 110,
            lengthM = 5.0f,
            elevationOrDepthM = 1.6f,
            wallThicknessMm = 6.6f,
            material = "PPR PN20 Drinking Water",
            systemLevel = "Wall Cavity Section (+1.6m)",
            ifcType = "IfcPipeSegment",
            installStatus = "Active (3.5 Bar)"
        ),

        // NATURAL GAS SUPPLY (Lower Wall)
        BuildingComponent(
            id = "GAS-BLD-601",
            name = "Natural Gas Supply (3D Pipeline)",
            category = BuildingCategory.GAS,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-2.5f, -0.30f, -2.1f),
            endPoint = floatArrayOf(2.5f, -0.30f, -2.1f),
            diameterMm = 63,
            lengthM = 5.0f,
            elevationOrDepthM = 1.1f,
            wallThicknessMm = 3.6f,
            material = "Polyethylene PE100 Gas",
            systemLevel = "Lower Wall Trench (+1.1m)",
            ifcType = "IfcPipeSegment",
            installStatus = "Certified"
        ),

        // === FLOOR / SUBSURFACE SECTION (-0.75m to -0.85m) ===
        // UNDERFLOOR SEWER DRAINAGE (Floor Section)
        BuildingComponent(
            id = "DWV-SAN-501",
            name = "Soil & Waste Sewer (Corrugated 3D)",
            category = BuildingCategory.DRAINAGE,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-2.8f, -0.75f, -2.2f),
            endPoint = floatArrayOf(2.8f, -0.85f, -2.6f),
            diameterMm = 160,
            lengthM = 5.8f,
            elevationOrDepthM = -0.6f,
            wallThicknessMm = 4.0f,
            slopePercent = 1.5f,
            material = "HDPE Corrugated Sewer",
            systemLevel = "Floor / Subsurface (-0.6m)",
            ifcType = "IfcPipeSegment",
            installStatus = "Gravity Flow"
        ),

        // === STRUCTURAL COLUMNS (West and East Pillars) ===
        // STRUCTURAL COLUMN A1 (West Wall Pillar)
        BuildingComponent(
            id = "COL-A1-01",
            name = "Reinforced Column A1 (3D Concrete)",
            category = BuildingCategory.STRUCTURAL,
            geometry = ComponentGeometry.STRUCTURAL_COLUMN,
            startPoint = floatArrayOf(-1.8f, -1.0f, -2.3f),
            endPoint = floatArrayOf(-1.8f, 1.0f, -2.3f),
            widthMm = 400,
            heightMm = 400,
            lengthM = 2.0f,
            elevationOrDepthM = 0.0f,
            wallThicknessMm = 400f,
            material = "Reinforced Concrete C35/45",
            systemLevel = "Structural Frame (West)",
            ifcType = "IfcColumn",
            installStatus = "Load Bearing"
        ),

        // STRUCTURAL COLUMN A2 (East Wall Pillar)
        BuildingComponent(
            id = "COL-A2-02",
            name = "Reinforced Column A2 (3D Concrete)",
            category = BuildingCategory.STRUCTURAL,
            geometry = ComponentGeometry.STRUCTURAL_COLUMN,
            startPoint = floatArrayOf(1.8f, -1.0f, -2.3f),
            endPoint = floatArrayOf(1.8f, 1.0f, -2.3f),
            widthMm = 400,
            heightMm = 400,
            lengthM = 2.0f,
            elevationOrDepthM = 0.0f,
            wallThicknessMm = 400f,
            material = "Reinforced Concrete C35/45",
            systemLevel = "Structural Frame (East)",
            ifcType = "IfcColumn",
            installStatus = "Load Bearing"
        )
    )

    /**
     * Scenario: Subsurface Municipal Infrastructure Grid (Pipes, Mains, Sewers).
     */
    fun getSubsurfaceUtilityComponents(): List<BuildingComponent> = listOf(
        BuildingComponent(
            id = "W-MUN-101",
            name = "Municipal Water Distribution Main",
            category = BuildingCategory.PLUMBING,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-5.0f, -1.2f, -3.0f),
            endPoint = floatArrayOf(5.0f, -1.2f, -3.0f),
            diameterMm = 250,
            lengthM = 10.0f,
            elevationOrDepthM = -1.2f,
            wallThicknessMm = 11.9f,
            material = "Ductile Iron Class K9",
            systemLevel = "Subsurface Street Corridor",
            ifcType = "IfcPipeSegment",
            installStatus = "Active (Pressure 6.5 Bar)"
        ),
        BuildingComponent(
            id = "SEW-GRAV-201",
            name = "Deep Municipal Gravity Sewer",
            category = BuildingCategory.DRAINAGE,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-4.0f, -2.4f, -5.5f),
            endPoint = floatArrayOf(4.0f, -2.8f, -5.5f),
            diameterMm = 400,
            lengthM = 8.0f,
            elevationOrDepthM = -2.6f,
            wallThicknessMm = 45.0f,
            slopePercent = 2.0f,
            material = "Spun Reinforced Concrete",
            systemLevel = "Deep Utility Zone",
            ifcType = "IfcPipeSegment",
            installStatus = "Surveyed / Operational"
        ),
        BuildingComponent(
            id = "GAS-HIGH-301",
            name = "High-Pressure Gas Distribution Trunk",
            category = BuildingCategory.GAS,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-4.5f, -0.8f, -2.0f),
            endPoint = floatArrayOf(4.5f, -0.8f, -2.0f),
            diameterMm = 110,
            lengthM = 9.0f,
            elevationOrDepthM = -0.8f,
            wallThicknessMm = 10.0f,
            material = "Polyethylene PE100 High-Pressure",
            systemLevel = "Shallow Utility Trench",
            ifcType = "IfcPipeSegment",
            installStatus = "Cathodically Protected"
        )
    )

    /**
     * Scenario: Heavy Industrial Plant & Plant Mechanical Room.
     */
    fun getIndustrialPlantComponents(): List<BuildingComponent> = listOf(
        BuildingComponent(
            id = "IND-CHW-101",
            name = "Chilled Water Supply Main",
            category = BuildingCategory.HVAC,
            geometry = ComponentGeometry.CYLINDRICAL_PIPE,
            startPoint = floatArrayOf(-4.0f, 1.8f, -3.0f),
            endPoint = floatArrayOf(4.0f, 1.8f, -3.0f),
            diameterMm = 200,
            lengthM = 8.0f,
            elevationOrDepthM = 1.8f,
            wallThicknessMm = 6.3f,
            material = "Insulated Carbon Steel Schedule 40",
            systemLevel = "Plant Overhead Rack",
            ifcType = "IfcPipeSegment",
            installStatus = "Chilled Loop (6°C)"
        ),
        BuildingComponent(
            id = "IND-EXH-201",
            name = "Heavy Industrial Process Exhaust Duct",
            category = BuildingCategory.HVAC,
            geometry = ComponentGeometry.RECTANGULAR_DUCT,
            startPoint = floatArrayOf(-4.5f, 3.6f, -4.2f),
            endPoint = floatArrayOf(4.5f, 3.6f, -4.2f),
            widthMm = 800,
            heightMm = 500,
            lengthM = 9.0f,
            elevationOrDepthM = 3.6f,
            wallThicknessMm = 2.0f,
            material = "316L Stainless Steel Welded Duct",
            systemLevel = "High Roof Ventilation",
            ifcType = "IfcDuctSegment",
            installStatus = "High Temperature Rated"
        )
    )
}
