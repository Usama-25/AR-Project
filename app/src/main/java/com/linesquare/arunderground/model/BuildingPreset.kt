package com.linesquare.arunderground.model

/**
 * Predefined building and facility engineering environments.
 */
enum class BuildingPreset(
    val title: String,
    val subtitle: String,
    val iconDescription: String
) {
    ASLAM_MARKET(
        title = "Aslam Market (165 Real Utilities)",
        subtitle = "Sewer Pipes, Fiber Cables, Manholes, Drop Cables (GeoJSON)",
        iconDescription = "Map"
    ),
    COMMERCIAL_BUILDING(
        title = "Commercial Office (Floor, Walls, Ceiling)",
        subtitle = "Underfloor Sewers, Columns, HVAC Ducts, Sprinklers, Cable Trays",
        iconDescription = "Building"
    ),
    UNDERGROUND_UTILITIES(
        title = "Subsurface Municipal Utilities",
        subtitle = "Water Trunk, Gas Main, Deep Sewer, Storm Conduits",
        iconDescription = "Underground"
    ),
    INDUSTRIAL_PLANT(
        title = "Industrial Plant & Mechanical Room",
        subtitle = "Chilled Water, Heavy Ducting, Cable Ladders, Steam Lines",
        iconDescription = "Factory"
    )
}
