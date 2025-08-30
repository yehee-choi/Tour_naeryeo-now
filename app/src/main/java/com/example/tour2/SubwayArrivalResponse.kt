package com.example.tour2

data class SubwayArrivalResponse(
    val errorMessage: ErrorMessage?,
    val realtimeArrivalList: List<ArrivalInfo>?
)

data class ErrorMessage(
    val status: Int,
    val code: String,
    val message: String,
    val link: String,
    val developerMessage: String,
    val total: Int
)

data class ArrivalInfo(
    val subwayId: String,           // 지하철호선ID
    val trainLineNm: String,        // 노선명
    val subwayHeading: String,      // 지하철방향
    val statnNm: String,            // 지하철역명
    val trainCo: String?,           // 열차번호
    val subwayList: String,         // 지하철리스트
    val statnList: String,          // 지하철역리스트
    val btrainSttus: String?,       // 열차종류
    val barvlDt: String,            // 열차도착예정시간
    val btrainNo: String?,          // 열차번호
    val bstatnId: String,           // 지하철역ID
    val bstatnNm: String,           // 지하철역명
    val recptnDt: String,           // 열차도착정보를 생성한 시각
    val arvlMsg2: String,           // 첫번째도착메세지
    val arvlMsg3: String,           // 두번째도착메세지
    val arvlCd: String,             // 도착코드
    val updnLine: String,           // 상행선내선구분
    val trainSttus: String?,        // 열차상태
    val directAt: String?,          // 급행여부
    val lstcarAt: String?           // 막차여부
)