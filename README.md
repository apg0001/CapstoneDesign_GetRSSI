# 📡 RSSI 측정 및 데이터 관리 어플리케이션

WiFi RSSI 데이터를 측정하고, 저장하며, 내보내기 및 초기화 기능까지 제공하는 Android 어플리케이션입니다. 측정된 데이터를 CSV로 저장해 머신러닝 모델 학습 및 위치 인식 서비스에 활용할 수 있습니다.

---

## 🏗️ 주요 기능

| 기능 | 설명 |
|------|---------------------------------------------------------------|
| **WiFi RSSI 측정** | 주변 WiFi AP의 RSSI 값을 측정해 SQLite DB에 저장 |
| **30회 측정** | 자동으로 30회 측정, 진행 상황 표시 |
| **측정 중지** | 측정 도중 언제든 중지 가능 |
| **CSV 내보내기** | 저장된 데이터를 CSV로 다운로드 폴더에 저장 |
| **데이터 보기** | 저장된 데이터를 팝업으로 확인 가능 |
| **데이터 초기화** | 저장된 데이터를 한 번에 삭제 가능 (확인 메시지 제공) |
| **측정 완료 알림** | 30회 측정 완료 시 푸시 알림과 팝업 제공 |

---

## 📱 화면 구성

- **EditText** : 위치 정보 입력 (좌표 또는 리전 이름)
- **Button**
  - 실행 (RSSI 측정 시작)
  - 중지 (WiFi 스캔 중지)
  - 내보내기 (CSV로 저장)
  - 보기 (DB에 저장된 데이터 확인)
  - 저장된 데이터 초기화 (데이터 전체 삭제)
- **TextView** : 현재 측정 상태 표시

---

## 🗂️ 데이터 구조

SQLite 데이터베이스 `wifi_rssi.db` 내부에 다음과 같은 구조의 테이블이 생성됩니다:

| Column   | Type    | Description          |
|---------|--------|----------------------|
| id      | INTEGER | Primary Key          |
| ssid    | TEXT    | WiFi SSID            |
| mac     | TEXT    | WiFi MAC 주소        |
| rssi    | INTEGER | 수신 신호 세기 (dBm) |
| time    | TEXT    | 측정 시간            |
| location| TEXT    | 입력한 위치 정보     |

---

## 📄 저장 데이터 예시 (CSV)

```
SSID,MAC,RSSI,Time,Location
mywifi,00:11:22:33:44:55,-45,2025-03-15 12:34:56,hall1
...
```

---

## 📦 주요 기술 스택

- **언어** : Kotlin
- **DB** : SQLite (Android 내장 DB)
- **UI** : Android 기본 View (EditText, Button, TextView, AlertDialog)
- **파일 저장** : CSV 파일 내보내기 (다운로드 폴더)
- **알림 기능** : NotificationCompat 사용
- **권한 요청** : WiFi 위치 접근 권한 요청

---

## 🚀 사용 방법

1. **위치 입력** : 상단 입력칸에 현재 위치(좌표, 리전명 등) 입력
2. **실행 버튼 클릭** : WiFi RSSI 측정 시작 (자동 30회)
3. **중지 버튼 클릭 (선택)** : 측정 중단 가능
4. **내보내기 버튼 클릭** : 측정 데이터 CSV로 내보내기
5. **보기 버튼 클릭** : 데이터 팝업으로 확인
6. **저장된 데이터 초기화 버튼 클릭** : DB 데이터 삭제 (확인 팝업 제공)

---

## ⚙️ 권한 설정

- AndroidManifest에 **ACCESS_FINE_LOCATION** 권한 필요
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```
앱 실행 시 권한 요청 팝업이 나타납니다.

---

## 🔔 특이사항

- **스캔 실패 시** → 10초 후 재시도
- **30회 측정 완료 후** → 푸시 알림 & 팝업 알림
- **다운로드 폴더에 CSV 저장 후** → 파일 탐색기에서 바로 확인 가능 (MediaScanner 활용)

---

## 👨‍💻 향후 발전 방향

- 서버로 직접 CSV 업로드 기능 추가
- 머신러닝 모델과 연동해 실시간 위치 추정 서비스 제공
- UI 개선 (Material Design 적용)

---