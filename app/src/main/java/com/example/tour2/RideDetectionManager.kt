package com.example.tour2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class RideDetectionState(
    val isMonitoring: Boolean = false,
    val subwayDetected: Boolean = false,
    val confidence: Float = 0f,
    val detectionReason: String = "",
    val lastKnownLocation: Location? = null
)

class RideDetectionManager(
    private val context: Context,
    private val locationManager: LocationManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _detectionState = MutableStateFlow(RideDetectionState())
    val detectionState: StateFlow<RideDetectionState> = _detectionState.asStateFlow()

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 센서 데이터 저장
    private val accelerometerData = mutableListOf<FloatArray>()
    private val gyroscopeData = mutableListOf<FloatArray>()
    private var lastLocationUpdate = 0L
    private var initialWifiSsids = mutableSetOf<String>()

    // 지하철 감지 임계값
    companion object {
        private const val ACCELEROMETER_THRESHOLD = 12f // m/s²
        private const val GYROSCOPE_THRESHOLD = 2f // rad/s
        private const val GPS_SIGNAL_LOSS_THRESHOLD = 30000L // 30초
        private const val MIN_DETECTION_SAMPLES = 10
        private const val SUBWAY_PATTERN_CONFIDENCE_THRESHOLD = 0.7f
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5초
    }

    fun startMonitoring() {
        if (_detectionState.value.isMonitoring) return

        _detectionState.value = _detectionState.value.copy(
            isMonitoring = true,
            detectionReason = "모니터링 시작"
        )

        // 센서 등록
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // 초기 WiFi 환경 저장
        captureInitialWifiEnvironment()

        // 지속적 모니터링 시작
        startContinuousMonitoring()
    }

    fun stopMonitoring() {
        _detectionState.value = _detectionState.value.copy(
            isMonitoring = false,
            subwayDetected = false,
            confidence = 0f,
            detectionReason = "모니터링 중지"
        )

        sensorManager.unregisterListener(this)
        monitoringJob?.cancel()
        clearSensorData()
    }

    private fun startContinuousMonitoring() {
        monitoringJob = scope.launch {
            while (_detectionState.value.isMonitoring) {
                try {
                    // 1. GPS 신호 체크
                    val gpsLost = checkGPSSignalLoss()

                    // 2. 센서 패턴 분석
                    val sensorPattern = analyzeSensorPatterns()

                    // 3. 네트워크 환경 변화 체크
                    val networkChanged = checkNetworkEnvironmentChange()

                    // 4. 종합 판단
                    val overallConfidence = calculateOverallConfidence(gpsLost, sensorPattern, networkChanged)

                    // 5. 지하철 감지 여부 결정
                    val subwayDetected = overallConfidence > SUBWAY_PATTERN_CONFIDENCE_THRESHOLD

                    _detectionState.value = _detectionState.value.copy(
                        subwayDetected = subwayDetected,
                        confidence = overallConfidence,
                        detectionReason = generateDetectionReason(gpsLost, sensorPattern, networkChanged)
                    )

                    delay(2000) // 2초마다 체크
                } catch (e: Exception) {
                    // 에러 처리
                    delay(5000) // 에러 시 더 긴 간격
                }
            }
        }
    }

    private suspend fun checkGPSSignalLoss(): Float {
        return try {
            val location = locationManager.getCurrentLocation()
            val currentTime = System.currentTimeMillis()

            if (location != null) {
                lastLocationUpdate = currentTime
                _detectionState.value = _detectionState.value.copy(lastKnownLocation = location)
                0f // GPS 신호 정상
            } else {
                val timeSinceLastUpdate = currentTime - lastLocationUpdate
                if (timeSinceLastUpdate > GPS_SIGNAL_LOSS_THRESHOLD) {
                    0.8f // GPS 신호 상실 → 지하일 가능성 높음
                } else {
                    0.3f // GPS 일시적 불안정
                }
            }
        } catch (e: Exception) {
            0.5f // GPS 접근 오류
        }
    }

    private fun analyzeSensorPatterns(): Float {
        if (accelerometerData.size < MIN_DETECTION_SAMPLES) return 0f

        val recentAccelData = accelerometerData.takeLast(MIN_DETECTION_SAMPLES)
        val recentGyroData = gyroscopeData.takeLast(MIN_DETECTION_SAMPLES)

        // 1. 진동 패턴 분석 (지하철 특유의 진동)
        val vibrationScore = analyzeVibrationPattern(recentAccelData)

        // 2. 회전 패턴 분석 (커브, 가속/감속)
        val rotationScore = analyzeRotationPattern(recentGyroData)

        // 3. 지속성 체크 (지하철은 지속적인 움직임)
        val consistencyScore = analyzeConsistency(recentAccelData)

        return (vibrationScore + rotationScore + consistencyScore) / 3f
    }

    private fun analyzeVibrationPattern(accelData: List<FloatArray>): Float {
        val magnitudes = accelData.map { data ->
            sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2])
        }

        val average = magnitudes.average()
        val variance = magnitudes.map { (it - average) * (it - average) }.average()

        // 지하철은 일정한 진동 패턴을 가짐
        return when {
            average > 11f && variance < 4f -> 0.8f // 지하철 패턴
            average > 9.5f && variance < 6f -> 0.6f // 대중교통 가능성
            average < 9.2f -> 0.1f // 정지 상태
            else -> 0.3f // 불분명
        }
    }

    private fun analyzeRotationPattern(gyroData: List<FloatArray>): Float {
        if (gyroData.isEmpty()) return 0f

        val rotationMagnitudes = gyroData.map { data ->
            sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2])
        }

        val maxRotation = rotationMagnitudes.maxOrNull() ?: 0f
        val avgRotation = rotationMagnitudes.average()

        // 지하철은 적당한 회전 움직임을 가짐 (커브, 정차)
        return when {
            maxRotation > 1.5f && avgRotation > 0.3f -> 0.7f
            maxRotation > 1f && avgRotation > 0.2f -> 0.5f
            avgRotation < 0.1f -> 0.1f
            else -> 0.3f
        }
    }

    private fun analyzeConsistency(accelData: List<FloatArray>): Float {
        val magnitudes = accelData.map { data ->
            sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2])
        }

        val movingAverages = magnitudes.windowed(3) { it.average() }
        val variance = movingAverages.map { avg ->
            magnitudes.map { (it - avg) * (it - avg) }.average()
        }.average()

        // 지하철은 비교적 일관된 움직임
        return when {
            variance < 2f -> 0.8f // 매우 일관됨
            variance < 4f -> 0.6f // 일관됨
            variance < 8f -> 0.3f // 보통
            else -> 0.1f // 불일치
        }
    }

    private fun checkNetworkEnvironmentChange(): Float {
        val currentWifiSsids = getCurrentWifiNetworks()
        val networkChangeScore = when {
            currentWifiSsids.isEmpty() && initialWifiSsids.isNotEmpty() -> 0.6f // WiFi 신호 상실
            currentWifiSsids.intersect(initialWifiSsids).isEmpty() && currentWifiSsids.isNotEmpty() -> 0.7f // 완전히 다른 WiFi 환경
            currentWifiSsids.size > initialWifiSsids.size + 3 -> 0.5f // 많은 새 네트워크 발견 (지하철역)
            else -> 0.2f
        }

        return networkChangeScore
    }

    private fun calculateOverallConfidence(gpsLost: Float, sensorPattern: Float, networkChanged: Float): Float {
        // 가중치 적용한 종합 점수
        val weights = floatArrayOf(0.4f, 0.5f, 0.1f) // GPS 40%, 센서 50%, 네트워크 10%
        return (gpsLost * weights[0] + sensorPattern * weights[1] + networkChanged * weights[2]).coerceIn(0f, 1f)
    }

    private fun generateDetectionReason(gpsLost: Float, sensorPattern: Float, networkChanged: Float): String {
        val reasons = mutableListOf<String>()

        if (gpsLost > 0.5f) reasons.add("GPS 신호 약화")
        if (sensorPattern > 0.5f) reasons.add("지하철 움직임 패턴")
        if (networkChanged > 0.5f) reasons.add("네트워크 환경 변화")

        return if (reasons.isNotEmpty()) {
            "감지: ${reasons.joinToString(", ")}"
        } else {
            "정상 상태"
        }
    }

    private fun captureInitialWifiEnvironment() {
        initialWifiSsids = getCurrentWifiNetworks().toMutableSet()
    }

    private fun getCurrentWifiNetworks(): Set<String> {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>") {
                setOf(wifiInfo.ssid.replace("\"", ""))
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun clearSensorData() {
        accelerometerData.clear()
        gyroscopeData.clear()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerData.add(sensorEvent.values.clone())
                    // 최대 50개 샘플만 유지
                    if (accelerometerData.size > 50) {
                        accelerometerData.removeAt(0)
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeData.add(sensorEvent.values.clone())
                    // 최대 50개 샘플만 유지
                    if (gyroscopeData.size > 50) {
                        gyroscopeData.removeAt(0)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변경 시 처리 (필요시)
    }

    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}