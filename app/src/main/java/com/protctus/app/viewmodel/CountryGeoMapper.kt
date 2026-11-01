package com.protctus.app.viewmodel

object CountryGeoMapper {
    // Rough coordinates for major countries to plot "real data" accurately.
    val countryMap = mapOf(
        "US" to Pair(37.0902, -95.7129),
        "CN" to Pair(35.8617, 104.1954),
        "RU" to Pair(61.5240, 105.3188),
        "BR" to Pair(-14.2350, -51.9253),
        "IN" to Pair(20.5937, 78.9629),
        "DE" to Pair(51.1657, 10.4515),
        "GB" to Pair(55.3781, -3.4360),
        "FR" to Pair(46.2276, 2.2137),
        "JP" to Pair(36.2048, 138.2529),
        "KR" to Pair(35.9078, 127.7669),
        "IR" to Pair(32.4279, 53.6880),
        "KP" to Pair(40.3399, 127.5101),
        "UA" to Pair(48.3794, 31.1656),
        "IL" to Pair(31.0461, 34.8516),
        "SA" to Pair(23.8859, 45.0792),
        "CA" to Pair(56.1304, -106.3468),
        "AU" to Pair(-25.2744, 133.7751),
        "ZA" to Pair(-30.5595, 22.9375),
        "NG" to Pair(9.0820, 8.6753),
        "MX" to Pair(23.6345, -102.5528),
        "Unknown" to Pair(0.0, 0.0)
    )

    fun getCoordinates(countryCode: String): Pair<Double, Double> {
        return countryMap[countryCode.uppercase()] ?: Pair(
            // Generate deterministic fake coords for unmapped countries based on string hash
            // so they don't jump around randomly.
            (countryCode.hashCode() % 160) - 80.0,
            (countryCode.hashCode() % 360) - 180.0
        )
    }
}
