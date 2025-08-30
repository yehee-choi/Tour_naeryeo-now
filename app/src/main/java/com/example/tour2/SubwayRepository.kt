package com.example.tour2

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class SubwayRepository {
    companion object {
        private const val BASE_URL = "http://swopenAPI.seoul.go.kr/"
        private const val API_KEY = BuildConfig.SUBWAY_API_KEY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(SubwayApiService::class.java)

    suspend fun getRealtimeArrival(stationName: String): Result<List<ArrivalInfo>> {
        return try {
            // API 키 확인 및 로깅
            if (API_KEY.isBlank() || API_KEY == "DEMO_KEY") {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. local.properties를 확인해주세요."))
            }

            println("🚇 API 호출: 역명=$stationName, API키=${API_KEY.take(10)}...")

            val response = apiService.getRealtimeArrival(API_KEY, 0, 10, stationName)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.errorMessage != null) {
                    // 🔧 정상 처리 코드도 성공으로 처리
                    if (body.errorMessage.code == "INFO-000" || body.errorMessage.status == 200) {
                        val arrivals = body.realtimeArrivalList ?: emptyList()
                        println("✅ 도착정보 ${arrivals.size}개 수신")
                        Result.success(arrivals)
                    } else {
                        println("❌ API 에러: ${body.errorMessage.message}")
                        Result.failure(Exception("API 에러: ${body.errorMessage.message}"))
                    }
                } else {
                    val arrivals = body?.realtimeArrivalList ?: emptyList()
                    println("✅ 도착정보 ${arrivals.size}개 수신")
                    Result.success(arrivals)
                }
            } else {
                println("❌ HTTP 에러: ${response.code()}")
                Result.failure(Exception("네트워크" +
                        " 오류: ${response.code()}"))
            }
        } catch (e: Exception) {
            println("❌ 예외 발생: ${e.message}")
            Result.failure(e)
        }
    }
    fun getSubwayStations(): List<SubwayStation> {
        return listOf(
            SubwayStation("강남", 37.4979, 127.0276, "2호선"),
            SubwayStation("홍대입구", 37.5570, 126.9240, "2호선"),
            SubwayStation("명동", 37.5630, 126.9870, "4호선"),
            SubwayStation("종로3가", 37.5717, 126.9913, "1호선"),
            SubwayStation("서울역", 37.5548, 126.9707, "1호선"),
            SubwayStation("신촌", 37.5550, 126.9369, "2호선"),
            SubwayStation("이태원", 37.5346, 127.0040, "6호선"),
            SubwayStation("잠실", 37.5133, 127.1000, "2호선"),
            SubwayStation("건대입구", 37.5403, 127.0703, "2호선"),
            SubwayStation("성수", 37.5447, 127.0558, "2호선")
        )
    }
}