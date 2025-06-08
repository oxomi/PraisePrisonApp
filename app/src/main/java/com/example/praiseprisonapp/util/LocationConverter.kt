package com.example.praiseprisonapp.util

import kotlin.math.*

/**
 * 위경도 좌표를 기상청 격자 좌표로 변환
 * 참고: https://gist.github.com/fronteer-kr/14d7f779d52a21ac2f16
 */
object LocationConverter {
    private const val NX = 149    // X축 격자점 수
    private const val NY = 253    // Y축 격자점 수

    private const val RE = 6371.00877     // 지구 반경(km)
    private const val GRID = 5.0          // 격자 간격(km)
    private const val SLAT1 = 30.0        // 표준위도 1
    private const val SLAT2 = 60.0        // 표준위도 2
    private const val OLON = 126.0        // 기준점 경도
    private const val OLAT = 38.0         // 기준점 위도
    private const val XO = 210 / GRID     // 기준점 X좌표
    private const val YO = 675 / GRID     // 기준점 Y좌표

    fun convertToGrid(lat: Double, lon: Double): Pair<Int, Int> {
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

        var nx = ra * sin(theta) + XO + 0.5
        var ny = ro - ra * cos(theta) + YO + 0.5

        // 격자점 수를 벗어나는 경우 처리
        nx = nx.coerceIn(1.0, NX.toDouble())
        ny = ny.coerceIn(1.0, NY.toDouble())

        return Pair(nx.toInt(), ny.toInt())
    }
} 