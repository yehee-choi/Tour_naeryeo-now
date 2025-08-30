package com.example.tour2

data class SubwayStation(
    val name: String, //역명
    val latitude: Double,   //위도
    val longitude: Double,  //경도
    val line: String        //지하촐 몇호선?
)