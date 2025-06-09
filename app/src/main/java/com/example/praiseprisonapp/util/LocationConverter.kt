package com.example.praiseprisonapp.util

import android.util.Log
import kotlin.math.*

/**
 * 위경도 좌표를 기상청 격자 좌표로 변환
 * 참고: https://gist.github.com/fronteer-kr/14d7f779d52a21ac2f16
 */
class LocationConverter {
    companion object {
        private const val RE = 6371.00877     // Earth radius (km)
        private const val GRID = 5.0          // Grid interval (km)
        private const val SLAT1 = 30.0        // Projection latitude 1
        private const val SLAT2 = 60.0        // Projection latitude 2
        private const val OLON = 126.0        // Reference longitude
        private const val OLAT = 38.0         // Reference latitude
        private const val XO = 43             // Reference X coordinate
        private const val YO = 136            // Reference Y coordinate

        fun convertToGrid(lat: Double, lon: Double): Pair<Int, Int> {
            Log.d("PraisePrison", "📍 Starting coordinate conversion: lat=$lat, lon=$lon")

            // Validation
            if (lat < 32.0 || lat > 43.0 || lon < 124.0 || lon > 132.0) {
                Log.w("PraisePrison", "⚠️ Coordinates out of Korea range. Using Busan coordinates.")
                // Use Busan coordinates (Haeundae)
                return convertToGrid(35.1631, 129.1637)
            }

            val DEGRAD = PI / 180.0
            val re = RE / GRID
            val slat1 = SLAT1 * DEGRAD
            val slat2 = SLAT2 * DEGRAD
            val olon = OLON * DEGRAD
            val olat = OLAT * DEGRAD

            var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
            sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
            var sf = tan(PI * 0.25 + slat1 * 0.5)
            sf = sf.pow(sn) * cos(slat1) / sn
            var ro = tan(PI * 0.25 + olat * 0.5)
            ro = re * sf / ro.pow(sn)

            var ra = tan(PI * 0.25 + lat * DEGRAD * 0.5)
            ra = re * sf / ra.pow(sn)
            var theta = lon * DEGRAD - olon
            if (theta > PI) theta -= 2.0 * PI
            if (theta < -PI) theta += 2.0 * PI
            theta *= sn

            val nx = (ra * sin(theta) + XO + 0.5).toInt()
            val ny = (ro - ra * cos(theta) + YO + 0.5).toInt()

            Log.d("PraisePrison", "📍 Converted grid coordinates: nx=$nx, ny=$ny")

            // Grid coordinate validation
            if (nx !in 1..149 || ny !in 1..253) {
                Log.w("PraisePrison", "⚠️ Invalid grid coordinates generated. Using Busan grid coordinates.")
                return Pair(98, 76) // Busan Haeundae grid coordinates
            }

            return Pair(nx, ny)
        }

        fun convertToGPS(nx: Int, ny: Int): Pair<Double, Double> {
            val DEGRAD = PI / 180.0
            val re = RE / GRID
            val slat1 = SLAT1 * DEGRAD
            val slat2 = SLAT2 * DEGRAD
            val olon = OLON * DEGRAD
            val olat = OLAT * DEGRAD

            var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
            sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
            var sf = tan(PI * 0.25 + slat1 * 0.5)
            sf = sf.pow(sn) * cos(slat1) / sn
            var ro = tan(PI * 0.25 + olat * 0.5)
            ro = re * sf / ro.pow(sn)

            var ra = sqrt((nx - XO) * (nx - XO) + (ro - (ny - YO)) * (ro - (ny - YO)))
            if (sn < 0.0) ra = -ra
            var alat = ((re * sf) / ra).pow(1.0 / sn)
            alat = 2.0 * atan(alat) - PI * 0.5

            var theta = 0.0
            if (abs(nx - XO) <= 0.0) {
                theta = 0.0
            } else {
                if (abs(ro - (ny - YO)) <= 0.0) {
                    theta = PI * 0.5
                    if (nx < XO) theta = -theta
                } else
                    theta = atan((nx - XO) / (ro - (ny - YO)))
            }
            theta /= sn
            val alon = theta + olon

            return Pair(alat / DEGRAD, alon / DEGRAD)
        }
    }
} 