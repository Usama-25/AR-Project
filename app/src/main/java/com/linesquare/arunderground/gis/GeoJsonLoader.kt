package com.linesquare.arunderground.gis

import android.content.Context
import com.google.gson.JsonParser
import com.linesquare.arunderground.model.BuildingCategory
import com.linesquare.arunderground.model.BuildingComponent
import com.linesquare.arunderground.model.ComponentGeometry
import com.linesquare.arunderground.model.GeoPoint

/**
 * Parses municipal GeoJSON files (e.g. Aslam Market utility infrastructure)
 * and generates 3D BuildingComponent objects with accurate engineering dimensions.
 */
object GeoJsonLoader {

    // Center benchmark datum for Aslam Market (near Quaid Ave main market corridor)
    val ASLAM_MARKET_CENTER = GeoPoint(
        latitude = 33.764825,
        longitude = 72.767500,
        altitude = 510.0
    )

    /**
     * Loads and parses all utilities from the bundled Aslam Market GeoJSON asset.
     */
    fun loadAslamMarketUtilities(context: Context): List<BuildingComponent> {
        val components = mutableListOf<BuildingComponent>()

        try {
            val jsonString = context.assets.open("geojson/Aslam_Market_all_utilities.geojson")
                .bufferedReader().use { it.readText() }

            val root = JsonParser.parseString(jsonString).asJsonObject
            val features = root.getAsJsonArray("features") ?: return emptyList()

            for (elem in features) {
                val feature = elem.asJsonObject
                val props = feature.getAsJsonObject("properties") ?: continue
                val geom = feature.getAsJsonObject("geometry") ?: continue
                val geomType = geom.get("type")?.asString ?: continue

                val id = try { props.get("id")?.takeIf { !it.isJsonNull }?.asString } catch (e: Exception) { null } ?: "ASSET-${components.size + 1}"
                val type = try { props.get("type")?.takeIf { !it.isJsonNull }?.asString } catch (e: Exception) { null } ?: "utility"
                val material = try {
                    props.get("material")?.takeIf { !it.isJsonNull }?.asString
                        ?: props.get("cable")?.takeIf { !it.isJsonNull }?.asString
                } catch (e: Exception) { null } ?: "Standard"
                val street = try { props.get("street")?.takeIf { !it.isJsonNull }?.asString } catch (e: Exception) { null } ?: "Corridor"
                val status = try { props.get("status")?.takeIf { !it.isJsonNull }?.asString } catch (e: Exception) { null } ?: "Active"

                val diameterMm = try {
                    val d = props.get("diameter_mm")
                    if (d != null && !d.isJsonNull && d.isJsonPrimitive && d.asJsonPrimitive.isNumber) d.asInt else null
                } catch (e: Exception) { null }

                val depthM = try {
                    val d = props.get("depth_m")
                    if (d != null && !d.isJsonNull && d.isJsonPrimitive && d.asJsonPrimitive.isNumber) d.asFloat else 1.2f
                } catch (e: Exception) { 1.2f }

                val lengthM = try {
                    val l = props.get("length_m")
                    if (l != null && !l.isJsonNull && l.isJsonPrimitive && l.asJsonPrimitive.isNumber) l.asFloat else 10.0f
                } catch (e: Exception) { 10.0f }

                // Map type to Category & Geometry
                val (category, geometry, elevationM, defaultWidth, defaultHeight) = when {
                    type.contains("sewer", ignoreCase = true) -> {
                        Tuple5(BuildingCategory.DRAINAGE, ComponentGeometry.CYLINDRICAL_PIPE, -depthM, null, null)
                    }
                    type.contains("fiber", ignoreCase = true) || type.contains("cable", ignoreCase = true) -> {
                        // Overhead distribution cables strung on poles (+3.5m elevation in street/building)
                        Tuple5(BuildingCategory.ELECTRICAL, ComponentGeometry.CABLE_TRAY, 3.5f, 100, 50)
                    }
                    type.contains("water", ignoreCase = true) -> {
                        Tuple5(BuildingCategory.PLUMBING, ComponentGeometry.CYLINDRICAL_PIPE, -depthM, null, null)
                    }
                    type.contains("gas", ignoreCase = true) -> {
                        Tuple5(BuildingCategory.GAS, ComponentGeometry.CYLINDRICAL_PIPE, -depthM, null, null)
                    }
                    else -> {
                        Tuple5(BuildingCategory.PLUMBING, ComponentGeometry.CYLINDRICAL_PIPE, -depthM, null, null)
                    }
                }

                if (geomType.equals("LineString", ignoreCase = true)) {
                    val coordsArray = geom.getAsJsonArray("coordinates")
                    if (coordsArray != null && coordsArray.size() >= 2) {
                        for (i in 0 until coordsArray.size() - 1) {
                            val c1 = coordsArray.get(i).asJsonArray
                            val c2 = coordsArray.get(i + 1).asJsonArray

                            val lon1 = c1.get(0).asDouble
                            val lat1 = c1.get(1).asDouble
                            val lon2 = c2.get(0).asDouble
                            val lat2 = c2.get(1).asDouble

                            val enu1 = CoordinateUtils.geodeticToEnu(
                                GeoPoint(lat1, lon1),
                                ASLAM_MARKET_CENTER
                            )
                            val enu2 = CoordinateUtils.geodeticToEnu(
                                GeoPoint(lat2, lon2),
                                ASLAM_MARKET_CENTER
                            )

                            // Local metric coordinates: X = East, Y = Elevation, Z = South (-North)
                            val sX = enu1.first.toFloat()
                            val sZ = -enu1.second.toFloat()
                            val eX = enu2.first.toFloat()
                            val eZ = -enu2.second.toFloat()

                            val dx = eX - sX
                            val dz = eZ - sZ
                            val segLen = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.5f)

                            val segmentId = if (coordsArray.size() > 2) "$id-$i" else id

                            components.add(
                                BuildingComponent(
                                    id = segmentId,
                                    name = "$type ($material)",
                                    category = category,
                                    geometry = geometry,
                                    startPoint = floatArrayOf(sX, 0f, sZ),
                                    endPoint = floatArrayOf(eX, 0f, eZ),
                                    diameterMm = diameterMm ?: if (category == BuildingCategory.DRAINAGE) 450 else 110,
                                    widthMm = defaultWidth,
                                    heightMm = defaultHeight,
                                    lengthM = if (coordsArray.size() == 2) lengthM else segLen,
                                    elevationOrDepthM = elevationM,
                                    material = material,
                                    systemLevel = "$street ($elevationM m)",
                                    ifcType = if (geometry == ComponentGeometry.CYLINDRICAL_PIPE) "IfcPipeSegment" else "IfcCableCarrierSegment",
                                    installStatus = status
                                )
                            )
                        }
                    }
                } else if (geomType.equals("Point", ignoreCase = true)) {
                    // Manholes, fiber poles, or service cabinets modeled as vertical 3D chambers
                    val coord = geom.getAsJsonArray("coordinates")
                    if (coord != null && coord.size() >= 2) {
                        val lon = coord.get(0).asDouble
                        val lat = coord.get(1).asDouble

                        val enu = CoordinateUtils.geodeticToEnu(
                            GeoPoint(lat, lon),
                            ASLAM_MARKET_CENTER
                        )

                        val pX = enu.first.toFloat()
                        val pZ = -enu.second.toFloat()

                        if (type.contains("manhole", ignoreCase = true)) {
                            // Subsurface vertical manhole chamber from ground (0m) to sewer invert (-depthM)
                            components.add(
                                BuildingComponent(
                                    id = id,
                                    name = "Inspection Manhole ($id)",
                                    category = BuildingCategory.DRAINAGE,
                                    geometry = ComponentGeometry.STRUCTURAL_COLUMN,
                                    startPoint = floatArrayOf(pX, 0f, pZ),
                                    endPoint = floatArrayOf(pX, -depthM, pZ),
                                    diameterMm = 1000,
                                    widthMm = 1000,
                                    heightMm = 1000,
                                    lengthM = depthM,
                                    elevationOrDepthM = 0f,
                                    material = "Precast Concrete Ring",
                                    systemLevel = "$street (Surface MH)",
                                    ifcType = "IfcDistributionChamberElement",
                                    installStatus = status
                                )
                            )
                        } else if (type.contains("pole", ignoreCase = true)) {
                            // Vertical utility pole (0m to +7.0m overhead)
                            components.add(
                                BuildingComponent(
                                    id = id,
                                    name = "Fiber Utility Pole ($id)",
                                    category = BuildingCategory.ELECTRICAL,
                                    geometry = ComponentGeometry.STRUCTURAL_COLUMN,
                                    startPoint = floatArrayOf(pX, 0f, pZ),
                                    endPoint = floatArrayOf(pX, 7.0f, pZ),
                                    diameterMm = 200,
                                    widthMm = 200,
                                    heightMm = 200,
                                    lengthM = 7.0f,
                                    elevationOrDepthM = 0f,
                                    material = "Spun Concrete Pole",
                                    systemLevel = "$street (Overhead Pole)",
                                    ifcType = "IfcColumn",
                                    installStatus = status
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return components
    }

    private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
