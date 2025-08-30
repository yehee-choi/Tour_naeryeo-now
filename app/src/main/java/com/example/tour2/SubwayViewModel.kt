package com.example.tour2

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubwayUiState(
    val arrivalInfo: List<ArrivalInfo> = emptyList(),
    val nearestStation: SubwayStation? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // 승차 관련 상태
    val isRiding: Boolean = false,
    val boardingStation: SubwayStation? = null,
    val destinationStation: SubwayStation? = null,
    val remainingStations: Int = 0,
    val estimatedArrival: String? = null,
    val routeStations: List<String> = emptyList(),
    // 🆕 감지 관련 상태
    val detectionInfo: DetectionInfo = DetectionInfo(),
    val currentTrain: ArrivalInfo? = null
)

// 🆕 감지 정보 데이터 클래스
data class DetectionInfo(
    val isDetecting: Boolean = false,
    val confidence: Float = 0f,
    val reason: String = "",
    val subwayDetected: Boolean = false
)

class SubwayViewModel : ViewModel() {
    private val repository = SubwayRepository()

    private val _uiState = MutableStateFlow(SubwayUiState())
    val uiState: StateFlow<SubwayUiState> = _uiState.asStateFlow()

    // 승차 감지 관리자
    private var rideDetectionManager: RideDetectionManager? = null

    // 위치 관리자 참조 저장
    private var locationManager: LocationManager? = null

    // 테스트 모드 설정
    companion object {
        private const val TEST_MODE = false // 실제 센서 사용
        private const val TEST_STATION_NAME = "강남"  // 에뮬레이터 테스트용 강남역
        private const val AUTO_DETECTION_MODE = true // 자동 감지 모드
        private const val EMULATOR_MODE = true // 에뮬레이터 모드 추가
    }

    // 승차 시작 함수
    fun startRiding(destination: SubwayStation) {
        val currentStation = if (EMULATOR_MODE) {
            // 에뮬레이터 모드에서는 강남역을 현재 위치로 사용
            SubwayStation(TEST_STATION_NAME, 37.4979, 127.0276, "2호선")
        } else if (TEST_MODE) {
            SubwayStation(TEST_STATION_NAME, 37.5000, 127.0366, "2호선")
        } else {
            _uiState.value.nearestStation
        }

        if (currentStation != null) {
            val route = calculateRoute(currentStation, destination)
            val estimatedTime = calculateEstimatedTime(route.size)

            _uiState.value = _uiState.value.copy(
                isRiding = true,
                boardingStation = currentStation,
                destinationStation = destination,
                remainingStations = route.size - 1, // 현재역 제외
                estimatedArrival = estimatedTime,
                routeStations = route
            )
        }
    }

    // 🆕 승차 종료 함수
    fun stopRiding() {
        _uiState.value = _uiState.value.copy(
            isRiding = false,
            boardingStation = null,
            destinationStation = null,
            remainingStations = 0,
            estimatedArrival = null,
            routeStations = emptyList()
        )
    }

    // 🆕 경로 계산 (간단한 2호선 예시)
    private fun calculateRoute(from: SubwayStation, to: SubwayStation): List<String> {
        // 2호선 순환선 예시 (실제로는 더 복잡한 알고리즘 필요)
        val line2Stations = listOf(
            "시청", "을지로입구", "을지로3가", "을지로4가", "동대문역사문화공원",
            "신당", "상왕십리", "왕십리", "한양대", "뚝섬", "성수", "건대입구",
            "구의", "강변", "잠실나루", "잠실", "신천", "종합운동장", "삼성",
            "선릉", "역삼", "강남", "교대", "서초", "방배", "사당", "낙성대",
            "서울대입구", "봉천", "신림", "신대방", "구로디지털단지", "대림",
            "신도림", "문래", "영등포구청", "당산", "합정", "홍대입구", "신촌",
            "이대", "아현", "충정로"
        )

        val fromIndex = line2Stations.indexOf(from.name)
        val toIndex = line2Stations.indexOf(to.name)

        return if (fromIndex != -1 && toIndex != -1) {
            if (fromIndex <= toIndex) {
                line2Stations.subList(fromIndex, toIndex + 1)
            } else {
                // 순환선이므로 두 방향 중 짧은 쪽 선택
                val directRoute = line2Stations.subList(toIndex, fromIndex + 1).reversed()
                val circularRoute = line2Stations.subList(fromIndex, line2Stations.size) +
                        line2Stations.subList(0, toIndex + 1)

                if (directRoute.size <= circularRoute.size) directRoute else circularRoute
            }
        } else {
            listOf(from.name, to.name) // 기본값
        }
    }

    // 🆕 예상 도착 시간 계산
    private fun calculateEstimatedTime(stationCount: Int): String {
        val minutesPerStation = 2 // 역간 평균 2분
        val totalMinutes = (stationCount - 1) * minutesPerStation
        return "${totalMinutes}분 후"
    }

    // 🆕 다음 역으로 이동 (테스트용)
    fun moveToNextStation() {
        val currentState = _uiState.value
        if (currentState.isRiding && currentState.remainingStations > 0) {
            val newRemaining = currentState.remainingStations - 1
            val newEstimatedTime = calculateEstimatedTime(newRemaining + 1)

            _uiState.value = currentState.copy(
                remainingStations = newRemaining,
                estimatedArrival = if (newRemaining > 0) newEstimatedTime else "도착!"
            )

            // 3개 역 남았을 때 알림 로직
            if (newRemaining == 3) {
                _uiState.value = _uiState.value.copy(
                    error = "⚠️ 3개 역 후 ${currentState.destinationStation?.name}역입니다!"
                )
            } else if (newRemaining == 1) {
                _uiState.value = _uiState.value.copy(
                    error = "🚨 다음 역은 ${currentState.destinationStation?.name}역입니다!"
                )
            } else if (newRemaining == 0) {
                _uiState.value = _uiState.value.copy(
                    error = "🎉 ${currentState.destinationStation?.name}역에 도착했습니다!"
                )
            }
        }
    }

    // 기존 함수들...
    // 🆕 자동 감지 관련 함수들 추가

    // 자동 승차 감지 시작
    fun startAutoDetection(context: Context, locationManager: LocationManager) {
        // LocationManager 참조 저장
        this.locationManager = locationManager

        // 에뮬레이터 모드에서는 초기값을 강남역으로 설정
        if (EMULATOR_MODE) {
            val gangnamStation = SubwayStation(TEST_STATION_NAME, 37.4979, 127.0276, "2호선")
            _uiState.value = _uiState.value.copy(nearestStation = gangnamStation)
        }

        if (rideDetectionManager == null) {
            rideDetectionManager = RideDetectionManager(context, locationManager)
        }

        rideDetectionManager?.startMonitoring()

        // 감지 상태 모니터링
        viewModelScope.launch {
            rideDetectionManager?.detectionState?.collect { detectionState ->
                if (detectionState.subwayDetected && !_uiState.value.isRiding) {
                    // 지하철 승차 자동 감지됨
                    _uiState.value = _uiState.value.copy(
                        error = "지하철 승차가 감지되었습니다! 목적지를 선택하세요."
                    )
                }

                // UI 상태 업데이트 (감지 정보 표시)
                _uiState.value = _uiState.value.copy(
                    detectionInfo = DetectionInfo(
                        isDetecting = detectionState.isMonitoring,
                        confidence = detectionState.confidence,
                        reason = detectionState.detectionReason,
                        subwayDetected = detectionState.subwayDetected
                    )
                )
            }
        }
    }

    // 자동 감지 중지
    fun stopAutoDetection() {
        rideDetectionManager?.stopMonitoring()
    }

    // 실시간 위치 기반 역 매칭
    fun matchStationWithRealtimeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val location = rideDetectionManager?.detectionState?.value?.lastKnownLocation
                if (location != null) {
                    val stations = repository.getSubwayStations()
                    val nearestStation = stations.minByOrNull { station ->
                        val distance = this@SubwayViewModel.locationManager?.calculateDistance(
                            location.latitude, location.longitude,
                            station.latitude, station.longitude
                        ) ?: Double.MAX_VALUE
                        distance
                    }

                    if (nearestStation != null) {
                        // 해당 역의 실시간 데이터 가져와서 현재 시간과 매칭
                        val arrivals = repository.getRealtimeArrival(nearestStation.name).getOrNull()

                        // 도착 정보에서 현재 탄 열차 추정
                        val likelyTrain = arrivals?.find { arrival ->
                            // 실제로는 더 복잡한 매칭 로직 필요
                            arrival.arvlMsg2?.contains("도착") == true ||
                                    arrival.arvlMsg2?.contains("출발") == true
                        }

                        _uiState.value = _uiState.value.copy(
                            nearestStation = nearestStation,
                            arrivalInfo = arrivals ?: emptyList(),
                            currentTrain = likelyTrain,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "실시간 데이터 매칭 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    // 🔧 수정된 통합 함수
    fun findStationAndGetArrival(locationManager: LocationManager? = null) {
        if (TEST_MODE) {
            setTestStation()
        } else {
            locationManager?.let {
                findNearestStationAndGetArrival(it)
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    error = "위치 관리자가 설정되지 않았습니다",
                    isLoading = false
                )
            }
        }
    }

    fun refreshArrivalInfo() {
        val stationName = if (EMULATOR_MODE) {
            TEST_STATION_NAME
        } else if (AUTO_DETECTION_MODE) {
            _uiState.value.nearestStation?.name
        } else if (TEST_MODE) {
            TEST_STATION_NAME
        } else {
            _uiState.value.nearestStation?.name
        }

        stationName?.let {
            getArrivalInfo(it)
        }
    }

    // 에뮬레이터용: 강남역으로 고정 설정
    private fun setEmulatorStation() {
        val emulatorStation = SubwayStation(TEST_STATION_NAME, 37.4979, 127.0276, "2호선")
        _uiState.value = _uiState.value.copy(nearestStation = emulatorStation)
        getArrivalInfo(TEST_STATION_NAME)
    }

    private fun setTestStation() {
        val testStation = SubwayStation(TEST_STATION_NAME, 37.5000, 127.0366, "2호선")
        _uiState.value = _uiState.value.copy(nearestStation = testStation)
        getArrivalInfo(TEST_STATION_NAME)
    }

    private fun findNearestStationAndGetArrival(locationManager: LocationManager) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val location = locationManager.getCurrentLocation()
                if (location != null) {
                    val stations = repository.getSubwayStations()
                    val nearest = locationManager.findNearestStation(
                        location.latitude,
                        location.longitude,
                        stations
                    )

                    if (nearest != null) {
                        _uiState.value = _uiState.value.copy(nearestStation = nearest)
                        getArrivalInfo(nearest.name)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "주변에 지하철역을 찾을 수 없습니다",
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "현재 위치를 가져올 수 없습니다",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "위치 정보를 가져오는데 실패했습니다",
                    isLoading = false
                )
            }
        }
    }

    private fun getArrivalInfo(stationName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getRealtimeArrival(stationName)
                .onSuccess { arrivals ->
                    _uiState.value = _uiState.value.copy(
                        arrivalInfo = arrivals,
                        isLoading = false
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        error = exception.message ?: "도착정보를 가져오는데 실패했습니다",
                        isLoading = false
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // 🆕 ViewModel 정리
    override fun onCleared() {
        super.onCleared()
        rideDetectionManager?.cleanup()
    }
}