package com.example.tour2

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SubwayApiService {
    @GET("api/subway/{apiKey}/json/realtimeStationArrival/{startIndex}/{endIndex}/{stationName}")
    suspend fun getRealtimeArrival(
        @Path("apiKey") apiKey: String,
        @Path("startIndex") startIndex: Int = 0,
        @Path("endIndex") endIndex: Int = 10,
        @Path("stationName") stationName: String
    ): Response<SubwayArrivalResponse>
}
