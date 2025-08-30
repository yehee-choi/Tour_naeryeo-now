package com.example.tour2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tour2.ui.theme.Tour2Theme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var locationManager: LocationManager

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // 정확한 위치 권한 승인됨
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // 대략적인 위치 권한만 승인됨
                //주기적인 위치 호출, SubWayAPI 호출
            }
            else -> {
                Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationManager = LocationManager(this)

        setContent {
            Tour2Theme {
                MainScreen(
                    locationManager = locationManager,
                    onRequestLocationPermission = {
                        requestLocationPermission()
                    }
                )
            }
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun MainScreen(
    locationManager: LocationManager,
    onRequestLocationPermission: () -> Unit,
    viewModel: SubwayViewModel = viewModel()
) {
    var isTracking by remember { mutableStateOf(false) }
    var showSubwayInfo by remember { mutableStateOf(false) }
    var showDestinationPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // 🆕 자동 감지 시작 (앱 시작 시 한 번만)
    LaunchedEffect(Unit) {
        viewModel.startAutoDetection(context, locationManager)
    }

    // 에러 처리
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
    ) {
        // Status Bar
        StatusBar()

        // Header - 🆕 감지 상태 반영
        Header(
            isRiding = uiState.isRiding,
            isDetecting = uiState.detectionInfo.isDetecting
        )

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Main Tracking Button - 🆕 감지 상태 추가
            TrackingButton(
                isTracking = isTracking,
                isRiding = uiState.isRiding,
                isDetecting = uiState.detectionInfo.isDetecting,
                onClick = {
                    if (uiState.isRiding) {
                        // 승차 중이면 승차 종료
                        viewModel.stopRiding()
                        isTracking = false
                    } else {
                        isTracking = !isTracking
                        if (isTracking) {
                            showSubwayInfo = true
                            showDestinationPicker = true
                            viewModel.findStationAndGetArrival(locationManager)
                        } else {
                            showSubwayInfo = false
                            showDestinationPicker = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Text - 🆕 감지 상태 추가
            StatusText(
                isTracking = isTracking,
                isRiding = uiState.isRiding,
                isDetecting = uiState.detectionInfo.isDetecting,
                destination = uiState.destinationStation?.name
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🆕 감지 정보 표시 (새로 추가)
            AnimatedVisibility(
                visible = uiState.detectionInfo.isDetecting && !uiState.isRiding,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DetectionInfoSection(
                    detectionInfo = uiState.detectionInfo,
                    onStartRiding = { destination ->
                        viewModel.startRiding(destination)
                        showDestinationPicker = false
                    }
                )
            }

            // 승차 정보 표시 (기존 코드)
            AnimatedVisibility(
                visible = uiState.isRiding,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RideInfoSection(
                    uiState = uiState,
                    onNextStation = { viewModel.moveToNextStation() } // 테스트용
                )
            }

            // 목적지 선택 다이얼로그 (기존 코드)
            AnimatedVisibility(
                visible = showDestinationPicker && !uiState.isRiding,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DestinationPickerSection(
                    onDestinationSelected = { destination ->
                        viewModel.startRiding(destination)
                        showDestinationPicker = false
                    },
                    onCancel = {
                        showDestinationPicker = false
                        isTracking = false
                    }
                )
            }

            // 지하철 정보 표시 (기존 코드)
            AnimatedVisibility(
                visible = showSubwayInfo && !uiState.isRiding,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SubwayInfoSection(
                    uiState = uiState,
                    onRefresh = {
                        viewModel.refreshArrivalInfo()
                    }
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Navigation (기존 코드)
        BottomNavigation(
            onSubwayClick = {
                if (!uiState.isRiding) {
                    showSubwayInfo = !showSubwayInfo
                    if (showSubwayInfo) {
                        viewModel.findStationAndGetArrival(locationManager)
                    }
                }
            }
        )

        // Home Indicator (기존 코드)
        HomeIndicator()
    }
}

// 새로 추가: 감지 정보 섹션 (초기 디자인과 동일한 스타일)
@Composable
fun DetectionInfoSection(
    detectionInfo: DetectionInfo,
    onStartRiding: (SubwayStation) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))  // 기존과 동일한 배경색
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 감지 상태 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (detectionInfo.subwayDetected) "지하철 승차 감지됨" else "승차 감지 중",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A5E89)  // 기존과 동일한 텍스트 색상
                )

                // 신뢰도 표시
                Text(
                    text = "${(detectionInfo.confidence * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)  // 기존과 동일한 서브 텍스트 색상
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 감지 이유
            Text(
                text = detectionInfo.reason,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 신뢰도 프로그레스 바 (기존 스타일)
            LinearProgressIndicator(
                progress = detectionInfo.confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF4990E2),  // 기존 파란색
                trackColor = Color(0xFF4990E2).copy(alpha = 0.3f)
            )

            // 지하철 감지됐을 때만 버튼 표시
            if (detectionInfo.subwayDetected) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val destination = SubwayStation("강남", 37.4979, 127.0276, "2호선")
                        onStartRiding(destination)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4990E2),  // 기존 파란색
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "목적지 설정하고 승차 시작",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// 기존 함수들 - 🔧 감지 상태 파라미터만 추가

// 기존 함수들 - 초기 디자인 그대로 유지

@Composable
fun Header(
    isRiding: Boolean = false,
    isDetecting: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (isRiding) Color(0xFF4990E2) else Color(0xFFF4F9FB))  // 초기와 동일
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isRiding) "승차 중" else "교통 알림",  // 초기와 동일한 텍스트
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRiding) Color.White else Color(0xFF1A5E89)
        )
    }
}

@Composable
fun TrackingButton(
    isTracking: Boolean,
    isRiding: Boolean,
    isDetecting: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .shadow(
                elevation = 25.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF4990E2).copy(alpha = 0.3f),
                spotColor = Color(0xFF4990E2).copy(alpha = 0.3f)
            )
            .background(
                brush = if (isRiding) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE74C3C),
                            Color(0xFFC0392B)
                        )
                    )
                } else {
                    Brush.linearGradient(  // 감지 중일 때도 기본 파란색 그라데이션
                        colors = listOf(
                            Color(0xFF4990E2),
                            Color(0xFF8E44AC)
                        )
                    )
                },
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    isRiding -> "riding"
                    isTracking -> "tracking"
                    else -> "idle"  // detecting 상태 제거, 기본 idle로 통합
                },
                label = "button_state"
            ) { state ->
                when (state) {
                    "riding" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "승차 종료",
                                modifier = Modifier.size(60.dp),
                                tint = Color(0xFFE74C3C)
                            )
                            Text(
                                text = "승차 종료",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE74C3C),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    "tracking" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsSubway,
                                contentDescription = "목적지 선택",
                                modifier = Modifier.size(60.dp),
                                tint = Color(0xFF4990E2)
                            )
                            Text(
                                text = "목적지 선택",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4990E2),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    else -> {  // idle 상태 (감지 중일 때도 기본 GPS 아이콘)
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "GPS 위치",
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF4990E2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusText(
    isTracking: Boolean,
    isRiding: Boolean,
    isDetecting: Boolean,
    destination: String? = null
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        color = when {
            isRiding -> Color(0xFF4990E2)
            isTracking -> Color(0xFFE8F4F8)
            else -> Color(0xFFEFEFEF)  // 감지 상태도 기본색으로 통일
        }
    ) {
        Text(
            text = when {
                isRiding && destination != null -> "목적지: ${destination}역"
                isTracking -> "현재 상태: 목적지 선택 대기중"
                else -> "현재 상태: 대기중"  // 감지 중일 때도 "대기중"으로 단순화
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRiding) Color.White else Color(0xFF666666)
        )
    }
}

// 아래는 기존 코드 그대로 유지 (변경사항 없음)

@Composable
fun RideInfoSection(
    uiState: SubwayUiState,
    onNextStation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4990E2))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${uiState.destinationStation?.name}역으로",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "이동 중",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.remainingStations > 0)
                        "${uiState.remainingStations}개 역 남음"
                    else "도착!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.estimatedArrival ?: "",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNextStation,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "다음 역 이동 (테스트용)",
                    color = Color(0xFF4990E2),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DestinationPickerSection(
    onDestinationSelected: (SubwayStation) -> Unit,
    onCancel: () -> Unit
) {
    val popularStations = listOf(
        SubwayStation("강남", 37.4979, 127.0276, "2호선"),
        SubwayStation("홍대입구", 37.5570, 126.9240, "2호선"),
        SubwayStation("신촌", 37.5550, 126.9369, "2호선"),
        SubwayStation("잠실", 37.5133, 127.1000, "2호선"),
        SubwayStation("건대입구", 37.5403, 127.0703, "2호선"),
        SubwayStation("성수", 37.5447, 127.0558, "2호선")
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "목적지를 선택하세요",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A5E89)
                )

                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color(0xFF666666)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                popularStations.forEach { station ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDestinationSelected(station) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Train,
                                contentDescription = null,
                                tint = Color(0xFF4990E2),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${station.name}역",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1A5E89)
                                )
                                Text(
                                    text = station.line,
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubwayInfoSection(
    uiState: SubwayUiState,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.nearestStation?.let { "${it.name}역 (${it.line})" }
                        ?: "지하철역 검색중...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A5E89)
                )

                if (uiState.nearestStation != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = Color(0xFF4990E2)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF4990E2)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "도착정보 불러오는 중...",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
                uiState.arrivalInfo.isEmpty() -> {
                    Text(
                        text = "현재 운행중인 열차가 없습니다",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                else -> {
                    Text(
                        text = "실시간 도착정보 (${uiState.arrivalInfo.size}개)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.arrivalInfo.forEach { arrival ->
                            ArrivalInfoItem(arrival = arrival)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArrivalInfoItem(arrival: ArrivalInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = arrival.trainLineNm ?: "정보없음",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A5E89)
                )
                Text(
                    text = arrival.subwayHeading ?: arrival.updnLine ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }

            Text(
                text = arrival.arvlMsg2 ?: "도착정보 없음",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE74C3C),
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = arrival.arvlMsg3 ?: "",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun StatusBar() {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTime = getCurrentTime()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentTime,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

private fun getCurrentTime(): String {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return dateFormat.format(Date())
}

@Composable
fun BottomNavigation(onSubwayClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFFF9F9F9)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Default.DirectionsSubway,
            label = "지하철 정보",
            onClick = onSubwayClick
        )

        BottomNavItem(
            icon = Icons.Default.Schedule,
            label = "최근 목적지",
            onClick = { /* Handle click */ }
        )

        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "설정",
            onClick = { /* Handle click */ }
        )
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = Color(0xFF2D85AA)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D85AA)
        )
    }
}

@Composable
fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(134.dp)
                .height(5.dp)
                .background(Color.Black, RoundedCornerShape(2.5.dp))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    Tour2Theme {
        // Preview용 더미 데이터
    }
}