package com.linesquare.arunderground.ar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.linesquare.arunderground.model.AlignmentOffset
import com.linesquare.arunderground.model.BuildingCategory
import com.linesquare.arunderground.model.BuildingComponent
import com.linesquare.arunderground.model.ComponentGeometry
import kotlin.math.*

/**
 * 3D Planar Face projected onto the 2D AR screen with depth for painter's algorithm rendering.
 */
data class ProjectedFace3D(
    val vertices: List<Offset>,
    val fillColor: Color,
    val strokeColor: Color,
    val strokeWidth: Float = 1.5f,
    val averageDepthZ: Float = 1.0f
)

/**
 * 3D Line detail (e.g. cross rungs, duct seams, rebar, cables, valves).
 */
data class ProjectedDetail3D(
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float = 2.0f,
    val depthZ: Float = 1.0f
)

/**
 * 2D Screen projection result containing full 3D volumetric mesh geometry.
 */
data class ProjectedComponent(
    val component: BuildingComponent,
    val startScreen: Offset,
    val endScreen: Offset,
    val badgeScreen: Offset,
    val distanceMeters: Float,
    val strokeWidthPx: Float,
    val isVisible: Boolean,
    val isFrontFacing: Boolean,
    // True 3D Volumetric Mesh Faces
    val meshFaces: List<ProjectedFace3D> = emptyList(),
    // True 3D Details (Flanges, valves, cable rungs, rebar grid, duct seams)
    val meshDetails: List<ProjectedDetail3D> = emptyList()
)

/**
 * Dynamic AR Projection Engine.
 * Translates 3D building component models into true 2D volumetric meshes with perspective foreshortening.
 */
object ARProjectionEngine {

    private const val DEFAULT_FOV_VERTICAL_DEG = 62.0f
    private const val NEAR_CLIP_PLANE_METERS = 0.20f

    fun projectComponents(
        components: List<BuildingComponent>,
        deviceOrientation: DeviceOrientation,
        alignmentOffset: AlignmentOffset,
        screenWidth: Float,
        screenHeight: Float
    ): List<ProjectedComponent> {
        if (screenWidth <= 0f || screenHeight <= 0f) return emptyList()

        val cx = screenWidth / 2f
        val cy = screenHeight / 2f

        val fovVRad = Math.toRadians(DEFAULT_FOV_VERTICAL_DEG.toDouble()).toFloat()
        val fy = (screenHeight / 2f) / tan(fovVRad / 2f)
        val fx = fy

        // Use relative azimuth so user faces models immediately on launch, plus manual calibration
        val totalYawDeg = (deviceOrientation.relativeAzimuthDeg + alignmentOffset.rotationYawDeg + 360f) % 360f
        val totalPitchDeg = (deviceOrientation.pitchDeg + alignmentOffset.pitchOffsetDeg).coerceIn(-89f, 89f)
        val yawRad = Math.toRadians(totalYawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(totalPitchDeg.toDouble()).toFloat()
        val rollRad = Math.toRadians(deviceOrientation.rollDeg.toDouble()).toFloat()

        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val cosRoll = cos(rollRad)
        val sinRoll = sin(rollRad)

        val results = ArrayList<ProjectedComponent>(components.size)

        for (comp in components) {
            val sWorldX = comp.startPoint[0] + alignmentOffset.offsetX
            val sWorldY = comp.startPoint[1] + alignmentOffset.offsetY
            val sWorldZ = comp.startPoint[2] + alignmentOffset.offsetZ

            val eWorldX = comp.endPoint[0] + alignmentOffset.offsetX
            val eWorldY = comp.endPoint[1] + alignmentOffset.offsetY
            val eWorldZ = comp.endPoint[2] + alignmentOffset.offsetZ

            val sCam = transformToCameraSpace(sWorldX, sWorldY, sWorldZ, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll)
            val eCam = transformToCameraSpace(eWorldX, eWorldY, eWorldZ, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll)

            // Both points behind camera? Skip
            if (sCam[2] <= NEAR_CLIP_PLANE_METERS && eCam[2] <= NEAR_CLIP_PLANE_METERS) {
                continue
            }

            var sClip = sCam
            var eClip = eCam
            if (sCam[2] < NEAR_CLIP_PLANE_METERS) {
                sClip = clipLineToNearPlane(sCam, eCam, NEAR_CLIP_PLANE_METERS)
            } else if (eCam[2] < NEAR_CLIP_PLANE_METERS) {
                eClip = clipLineToNearPlane(eCam, sCam, NEAR_CLIP_PLANE_METERS)
            }

            val sx = cx + (sClip[0] / sClip[2]) * fx
            val sy = cy - (sClip[1] / sClip[2]) * fy
            val ex = cx + (eClip[0] / eClip[2]) * fx
            val ey = cy - (eClip[1] / eClip[2]) * fy

            val midCamX = (sClip[0] + eClip[0]) / 2f
            val midCamY = (sClip[1] + eClip[1]) / 2f
            val midCamZ = (sClip[2] + eClip[2]) / 2f

            val mx = cx + (midCamX / midCamZ) * fx
            val my = cy - (midCamY / midCamZ) * fy
            val distance = sqrt(midCamX * midCamX + midCamY * midCamY + midCamZ * midCamZ)

            if (!sx.isFinite() || !sy.isFinite() || !ex.isFinite() || !ey.isFinite() ||
                !mx.isFinite() || !my.isFinite() || !distance.isFinite()) continue

            val physicalThicknessM = when {
                comp.diameterMm != null -> comp.diameterMm / 1000f
                comp.widthMm != null -> comp.widthMm / 1000f
                else -> 0.15f
            }
            val strokePx = ((physicalThicknessM / midCamZ.coerceAtLeast(0.5f)) * fx).coerceIn(8f, 64f)

            // Screen visibility check with generous margin
            val margin = 350f
            val isVisibleOnScreen = (
                (sx in -margin..(screenWidth + margin) && sy in -margin..(screenHeight + margin)) ||
                (ex in -margin..(screenWidth + margin) && ey in -margin..(screenHeight + margin)) ||
                (mx in -margin..(screenWidth + margin) && my in -margin..(screenHeight + margin))
            )

            // GENERATE TRUE 3D VOLUMETRIC MESH
            val meshFaces = mutableListOf<ProjectedFace3D>()
            val meshDetails = mutableListOf<ProjectedDetail3D>()

            generate3DMesh(
                comp = comp,
                sWorld = floatArrayOf(sWorldX, sWorldY, sWorldZ),
                eWorld = floatArrayOf(eWorldX, eWorldY, eWorldZ),
                cosYaw = cosYaw, sinYaw = sinYaw,
                cosPitch = cosPitch, sinPitch = sinPitch,
                cosRoll = cosRoll, sinRoll = sinRoll,
                cx = cx, cy = cy, fx = fx, fy = fy,
                outFaces = meshFaces,
                outDetails = meshDetails
            )

            results.add(
                ProjectedComponent(
                    component = comp,
                    startScreen = Offset(sx, sy),
                    endScreen = Offset(ex, ey),
                    badgeScreen = Offset(mx, my),
                    distanceMeters = distance,
                    strokeWidthPx = strokePx,
                    isVisible = isVisibleOnScreen,
                    isFrontFacing = midCamZ > 0.1f,
                    meshFaces = meshFaces,
                    meshDetails = meshDetails
                )
            )
        }

        // Painter's algorithm: sort components from furthest to nearest
        results.sortByDescending { it.distanceMeters }
        return results
    }

    /**
     * Builds true 3D meshes (duct volumes, pipe cylinders, cable trays, rungs, columns, flanges, valves).
     */
    private fun generate3DMesh(
        comp: BuildingComponent,
        sWorld: FloatArray,
        eWorld: FloatArray,
        cosYaw: Float, sinYaw: Float,
        cosPitch: Float, sinPitch: Float,
        cosRoll: Float, sinRoll: Float,
        cx: Float, cy: Float, fx: Float, fy: Float,
        outFaces: MutableList<ProjectedFace3D>,
        outDetails: MutableList<ProjectedDetail3D>
    ) {
        val color = comp.color

        when (comp.geometry) {
            ComponentGeometry.RECTANGULAR_DUCT -> {
                // 3D RECTANGULAR DUCT (e.g. 600x400 mm)
                val halfW = (comp.widthMm?.toFloat() ?: 600f) / 2000f
                val halfH = (comp.heightMm?.toFloat() ?: 400f) / 2000f

                // 4 corners at Start [TL, TR, BR, BL]
                val sCornersWorld = listOf(
                    floatArrayOf(sWorld[0], sWorld[1] + halfH, sWorld[2] - halfW),
                    floatArrayOf(sWorld[0], sWorld[1] + halfH, sWorld[2] + halfW),
                    floatArrayOf(sWorld[0], sWorld[1] - halfH, sWorld[2] + halfW),
                    floatArrayOf(sWorld[0], sWorld[1] - halfH, sWorld[2] - halfW)
                )

                // 4 corners at End [TL, TR, BR, BL]
                val eCornersWorld = listOf(
                    floatArrayOf(eWorld[0], eWorld[1] + halfH, eWorld[2] - halfW),
                    floatArrayOf(eWorld[0], eWorld[1] + halfH, eWorld[2] + halfW),
                    floatArrayOf(eWorld[0], eWorld[1] - halfH, eWorld[2] + halfW),
                    floatArrayOf(eWorld[0], eWorld[1] - halfH, eWorld[2] - halfW)
                )

                val sPts = sCornersWorld.mapNotNull { projectWorldToScreen(it, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy) }
                val ePts = eCornersWorld.mapNotNull { projectWorldToScreen(it, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy) }

                if (sPts.size == 4 && ePts.size == 4) {
                    val sAvgZ = sPts.map { it.second }.average().toFloat()
                    val eAvgZ = ePts.map { it.second }.average().toFloat()
                    val midZ = (sAvgZ + eAvgZ) / 2f

                    // Face 1: Top Face (TL_s, TR_s, TR_e, TL_e)
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(sPts[0].first, sPts[1].first, ePts[1].first, ePts[0].first),
                            fillColor = color.copy(alpha = 0.50f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )

                    // Face 2: Front/Side Face (TR_s, BR_s, BR_e, TR_e)
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(sPts[1].first, sPts[2].first, ePts[2].first, ePts[1].first),
                            fillColor = color.copy(alpha = 0.40f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )

                    // Face 3: Bottom Face (BL_s, BR_s, BR_e, BL_e)
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(sPts[3].first, sPts[2].first, ePts[2].first, ePts[3].first),
                            fillColor = color.copy(alpha = 0.30f),
                            strokeColor = color,
                            strokeWidth = 1.5f,
                            averageDepthZ = midZ
                        )
                    )

                    // Face 4: Start Opening Cap
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(sPts[0].first, sPts[1].first, sPts[2].first, sPts[3].first),
                            fillColor = color.copy(alpha = 0.60f),
                            strokeColor = Color.White,
                            strokeWidth = 2.5f,
                            averageDepthZ = sAvgZ
                        )
                    )

                    // Transverse reinforcement seams along the duct
                    for (i in 1..3) {
                        val t = i / 4.0f
                        val top1 = Offset(sPts[0].first.x * (1 - t) + ePts[0].first.x * t, sPts[0].first.y * (1 - t) + ePts[0].first.y * t)
                        val top2 = Offset(sPts[1].first.x * (1 - t) + ePts[1].first.x * t, sPts[1].first.y * (1 - t) + ePts[1].first.y * t)
                        val bot2 = Offset(sPts[2].first.x * (1 - t) + ePts[2].first.x * t, sPts[2].first.y * (1 - t) + ePts[2].first.y * t)
                        outDetails.add(ProjectedDetail3D(top1, top2, Color.White.copy(alpha = 0.8f), 2.0f, midZ))
                        outDetails.add(ProjectedDetail3D(top2, bot2, Color.White.copy(alpha = 0.8f), 2.0f, midZ))
                    }
                }
            }

            ComponentGeometry.CABLE_TRAY -> {
                // 3D ELECTRICAL CABLE TRAY WITH RUNGS AND POWER CABLES
                val halfW = (comp.widthMm?.toFloat() ?: 400f) / 2000f
                val railH = (comp.heightMm?.toFloat() ?: 100f) / 1000f

                // Left Rail Start & End
                val lStart = floatArrayOf(sWorld[0], sWorld[1], sWorld[2] - halfW)
                val lEnd = floatArrayOf(eWorld[0], eWorld[1], eWorld[2] - halfW)
                // Right Rail Start & End
                val rStart = floatArrayOf(sWorld[0], sWorld[1], sWorld[2] + halfW)
                val rEnd = floatArrayOf(eWorld[0], eWorld[1], eWorld[2] + halfW)

                val ls = projectWorldToScreen(lStart, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val le = projectWorldToScreen(lEnd, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val rs = projectWorldToScreen(rStart, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val re = projectWorldToScreen(rEnd, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)

                if (ls != null && le != null && rs != null && re != null) {
                    val midZ = (ls.second + le.second + rs.second + re.second) / 4f

                    // Tray Bottom Bed
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(ls.first, rs.first, re.first, le.first),
                            fillColor = color.copy(alpha = 0.25f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )

                    // Ladder Rungs across the tray
                    val numRungs = 7
                    for (i in 0..numRungs) {
                        val t = i.toFloat() / numRungs.toFloat()
                        val p1 = Offset(ls.first.x * (1 - t) + le.first.x * t, ls.first.y * (1 - t) + le.first.y * t)
                        val p2 = Offset(rs.first.x * (1 - t) + re.first.x * t, rs.first.y * (1 - t) + re.first.y * t)
                        outDetails.add(ProjectedDetail3D(p1, p2, Color.White.copy(alpha = 0.9f), 3.0f, midZ))
                    }

                    // 3 Colored Power / Data Cables running inside the tray
                    val cableColors = listOf(Color(0xFFFF1744), Color(0xFF2979FF), Color(0xFFFFD600))
                    for (cIdx in 0..2) {
                        val offsetFrac = 0.25f + cIdx * 0.25f
                        val cStart = Offset(ls.first.x * (1 - offsetFrac) + rs.first.x * offsetFrac, ls.first.y * (1 - offsetFrac) + rs.first.y * offsetFrac)
                        val cEnd = Offset(le.first.x * (1 - offsetFrac) + re.first.x * offsetFrac, le.first.y * (1 - offsetFrac) + re.first.y * offsetFrac)
                        outDetails.add(ProjectedDetail3D(cStart, cEnd, cableColors[cIdx], 3.5f, midZ))
                    }
                }
            }

            ComponentGeometry.STRUCTURAL_COLUMN -> {
                // 3D REINFORCED CONCRETE COLUMN
                val halfW = (comp.widthMm?.toFloat() ?: 450f) / 2000f

                // 4 corners of vertical column
                val b1 = floatArrayOf(sWorld[0] - halfW, sWorld[1], sWorld[2] - halfW)
                val b2 = floatArrayOf(sWorld[0] + halfW, sWorld[1], sWorld[2] - halfW)
                val b3 = floatArrayOf(sWorld[0] + halfW, sWorld[1], sWorld[2] + halfW)
                val b4 = floatArrayOf(sWorld[0] - halfW, sWorld[1], sWorld[2] + halfW)

                val t1 = floatArrayOf(eWorld[0] - halfW, eWorld[1], eWorld[2] - halfW)
                val t2 = floatArrayOf(eWorld[0] + halfW, eWorld[1], eWorld[2] - halfW)
                val t3 = floatArrayOf(eWorld[0] + halfW, eWorld[1], eWorld[2] + halfW)
                val t4 = floatArrayOf(eWorld[0] - halfW, eWorld[1], eWorld[2] + halfW)

                val bPts = listOf(b1, b2, b3, b4).mapNotNull { projectWorldToScreen(it, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy) }
                val tPts = listOf(t1, t2, t3, t4).mapNotNull { projectWorldToScreen(it, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy) }

                if (bPts.size == 4 && tPts.size == 4) {
                    val midZ = bPts[0].second

                    // Front Face
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(bPts[0].first, bPts[1].first, tPts[1].first, tPts[0].first),
                            fillColor = color.copy(alpha = 0.45f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )
                    // Side Face
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(bPts[1].first, bPts[2].first, tPts[2].first, tPts[1].first),
                            fillColor = color.copy(alpha = 0.35f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )

                    // Rebar wireframe hoops
                    for (i in 1..4) {
                        val t = i / 5.0f
                        val p1 = Offset(bPts[0].first.x * (1 - t) + tPts[0].first.x * t, bPts[0].first.y * (1 - t) + tPts[0].first.y * t)
                        val p2 = Offset(bPts[1].first.x * (1 - t) + tPts[1].first.x * t, bPts[1].first.y * (1 - t) + tPts[1].first.y * t)
                        val p3 = Offset(bPts[2].first.x * (1 - t) + tPts[2].first.x * t, bPts[2].first.y * (1 - t) + tPts[2].first.y * t)
                        outDetails.add(ProjectedDetail3D(p1, p2, Color.White.copy(alpha = 0.6f), 1.5f, midZ))
                        outDetails.add(ProjectedDetail3D(p2, p3, Color.White.copy(alpha = 0.6f), 1.5f, midZ))
                    }
                }
            }

            else -> {
                // CYLINDRICAL PIPES (Water, Drainage, Gas)
                val radiusM = (comp.diameterMm?.toFloat() ?: 110f) / 2000f

                // Generate 3D cylindrical cylinder strip (Top, Mid, Bottom)
                val sTop = floatArrayOf(sWorld[0], sWorld[1] + radiusM, sWorld[2])
                val sBot = floatArrayOf(sWorld[0], sWorld[1] - radiusM, sWorld[2])
                val eTop = floatArrayOf(eWorld[0], eWorld[1] + radiusM, eWorld[2])
                val eBot = floatArrayOf(eWorld[0], eWorld[1] - radiusM, eWorld[2])

                val st = projectWorldToScreen(sTop, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val sb = projectWorldToScreen(sBot, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val et = projectWorldToScreen(eTop, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)
                val eb = projectWorldToScreen(eBot, cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll, cx, cy, fx, fy)

                if (st != null && sb != null && et != null && eb != null) {
                    val midZ = (st.second + sb.second + et.second + eb.second) / 4f

                    // 3D Cylinder Body Face
                    outFaces.add(
                        ProjectedFace3D(
                            vertices = listOf(st.first, et.first, eb.first, sb.first),
                            fillColor = color.copy(alpha = 0.45f),
                            strokeColor = color,
                            strokeWidth = 2.0f,
                            averageDepthZ = midZ
                        )
                    )

                    // Pipe Circular End Flanges
                    val sFlangeTop = Offset(st.first.x, st.first.y - 8f)
                    val sFlangeBot = Offset(sb.first.x, sb.first.y + 8f)
                    val eFlangeTop = Offset(et.first.x, et.first.y - 8f)
                    val eFlangeBot = Offset(eb.first.x, eb.first.y + 8f)
                    outDetails.add(ProjectedDetail3D(sFlangeTop, sFlangeBot, Color.White, 4.0f, midZ))
                    outDetails.add(ProjectedDetail3D(eFlangeTop, eFlangeBot, Color.White, 4.0f, midZ))

                    // 3D Inline Valve Handwheel at Center (for Water & Gas)
                    if (comp.category == BuildingCategory.PLUMBING || comp.category == BuildingCategory.GAS) {
                        val midTop = Offset((st.first.x + et.first.x) / 2f, (st.first.y + et.first.y) / 2f)
                        val stemEnd = Offset(midTop.x, midTop.y - 28f)
                        val wheelLeft = Offset(stemEnd.x - 16f, stemEnd.y)
                        val wheelRight = Offset(stemEnd.x + 16f, stemEnd.y)

                        outDetails.add(ProjectedDetail3D(midTop, stemEnd, Color(0xFFFF5252), 3.0f, midZ))
                        outDetails.add(ProjectedDetail3D(wheelLeft, wheelRight, Color(0xFFFF5252), 5.0f, midZ))
                    }

                    // 3D Corrugated Ribs (for Drainage & Sewer)
                    if (comp.category == BuildingCategory.DRAINAGE) {
                        for (i in 1..5) {
                            val t = i / 6.0f
                            val rTop = Offset(st.first.x * (1 - t) + et.first.x * t, st.first.y * (1 - t) + et.first.y * t)
                            val rBot = Offset(sb.first.x * (1 - t) + eb.first.x * t, sb.first.y * (1 - t) + eb.first.y * t)
                            outDetails.add(ProjectedDetail3D(rTop, rBot, Color.White.copy(alpha = 0.8f), 2.5f, midZ))
                        }
                    }
                }
            }
        }
    }

    private fun projectWorldToScreen(
        pt: FloatArray,
        cosYaw: Float, sinYaw: Float,
        cosPitch: Float, sinPitch: Float,
        cosRoll: Float, sinRoll: Float,
        cx: Float, cy: Float, fx: Float, fy: Float
    ): Pair<Offset, Float>? {
        val cam = transformToCameraSpace(pt[0], pt[1], pt[2], cosYaw, sinYaw, cosPitch, sinPitch, cosRoll, sinRoll)
        if (cam[2] <= NEAR_CLIP_PLANE_METERS) return null

        val sx = cx + (cam[0] / cam[2]) * fx
        val sy = cy - (cam[1] / cam[2]) * fy
        return Pair(Offset(sx, sy), cam[2])
    }

    private fun transformToCameraSpace(
        wx: Float, wy: Float, wz: Float,
        cosYaw: Float, sinYaw: Float,
        cosPitch: Float, sinPitch: Float,
        cosRoll: Float, sinRoll: Float
    ): FloatArray {
        // Yaw
        val x1 = wx * cosYaw - wz * sinYaw
        val y1 = wy
        val z1 = -(wx * sinYaw + wz * cosYaw)

        // Pitch
        val x2 = x1
        val y2 = y1 * cosPitch - z1 * sinPitch
        val z2 = y1 * sinPitch + z1 * cosPitch

        // Roll
        val x3 = x2 * cosRoll - y2 * sinRoll
        val y3 = x2 * sinRoll + y2 * cosRoll
        val z3 = z2

        return floatArrayOf(x3, y3, z3)
    }

    private fun clipLineToNearPlane(p1: FloatArray, p2: FloatArray, nearPlane: Float): FloatArray {
        val dz = p2[2] - p1[2]
        if (abs(dz) < 0.0001f) return p1
        val t = (nearPlane - p1[2]) / dz
        val cx = p1[0] + t * (p2[0] - p1[0])
        val cy = p1[1] + t * (p2[1] - p1[1])
        return floatArrayOf(cx, cy, nearPlane)
    }
}
