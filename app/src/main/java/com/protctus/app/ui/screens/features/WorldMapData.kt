package com.protctus.app.ui.screens.features

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser

object WorldMapData {
    // A highly simplified low-poly Mercator world map path to keep rendering ultra-fast.
    // Coordinates are roughly in the range of X: [0, 1000], Y: [0, 1000]
    const val PATH_DATA = "M143.7,211.5l-4.1-1.3l-5.6-8l1.3-4.7l4-5.3l5.6,2.1l2.5,4.9L143.7,211.5z " +
            "M102.1,234.3l-1.6-4.7l-4.5-0.9l-4.3,2.5l-1.3,4.9l2.2,4.5l5.1-0.2L102.1,234.3z " +
            "M292.8,172.5l-5.1-1.3l-3.3-5.3l1.8-4.5l4.5-2l6.2,2.5l2,4.9L292.8,172.5z " +
            "M430.2,168.1l-4.7-2l-3.8-6.2l1.6-5.1l5.3-1.6l5.8,3.1l1.8,5.8L430.2,168.1z " +
            "M628.2,194.8l-5.8-2l-3.1-6.7l2.2-5.3l6.2-1.3l5.3,3.8l1.3,6.2L628.2,194.8z " +
            "M694.9,258.1l-6.2-1.8l-2.7-7.1l2.7-5.3l6.7-0.9l4.9,4.5l0.9,6.7L694.9,258.1z " +
            "M822.4,321.3l-6.7-1.3l-2.2-7.6l3.1-5.3l7.1-0.4l4.5,4.9l0.4,7.1L822.4,321.3z " +
            "M907,433.1l-7.1-0.9l-1.8-8l3.6-4.9l7.6,0.4l3.6,5.8l-0.4,7.6L907,433.1z " +
            "M284,554.4l-7.1-0.4l-1.3-8.5l4-4.5l8,0.9l2.7,6.2l-0.9,8L284,554.4z " +
            "M366.9,663.1l-7.6,0.4l-0.9-8.5l4.5-4l8.5,1.3l2.2,6.7l-1.3,8.5L366.9,663.1z " +
            "M450.6,762l-8,0.9l-0.4-8.9l4.9-3.6l8.9,1.8l1.8,7.1l-1.8,8.9L450.6,762z " +
            "M535.2,851.9l-8,1.3l0.4-8.9l5.3-2.7l8.9,2.2l1.3,7.6l-2.2,8.9L535.2,851.9z " +
            // Simplified Continents
            "M160,100 L300,100 L400,200 L300,400 L200,400 L150,300 Z " + // North America roughly
            "M250,450 L350,450 L300,650 L250,600 Z " + // South America
            "M450,150 L650,150 L750,250 L850,200 L950,300 L850,500 L650,400 L550,450 L450,350 Z " + // Eurasia
            "M450,400 L550,400 L600,550 L500,650 L450,550 Z " + // Africa
            "M750,550 L850,550 L850,650 L750,600 Z" // Australia

    fun getComposePath(): Path {
        return PathParser.createPathFromPathData(PATH_DATA).asComposePath()
    }
}
