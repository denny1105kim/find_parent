# 배포 전 보안 체크리스트

민감 정보(음성·영상·정밀 위치)를 다루는 앱이라, 배포 전 아래를 반드시 확인한다.
`✅`는 이미 코드/규칙에 반영됨, `⬜`는 콘솔/인프라에서 사람이 해야 하는 작업.

## 이미 반영됨 (코드/규칙)

- ✅ **접근 제어**: locations/clips/requests 모두 `pairs/{pairId}`의 UID(간병인/환자)로 게이트. pairId 를 알아도 남의 데이터 못 읽음.
- ✅ **연결 해제**: revoked로 권한을 차단하고 서버 함수가 하위 요청·클립·위치·토큰을 정리한다. 재사용 방지 문서는 유지한다(함수 배포 필수).
- ✅ **allowBackup=false**: 페어링 ID·인증 상태가 클라우드 백업으로 새지 않음.
- ✅ **토큰 쓰기 형태 고정**: `tokens`는 `token` 필드만 허용(임의 필드 주입 차단).
- ✅ **list 차단**: 모든 컬렉션 열거 금지(clips list는 간병인 정리용만).
- ✅ **exported=false**: 서비스/리시버 전부 비공개. FCM은 MESSAGING_EVENT 한정.
- ✅ **릴리스 서명 배선**: `local.properties`에 키스토어 정보 넣으면 릴리스에 적용.
- ✅ **R8 + 리소스 축소**: release 빌드 `optimization.enable=true` + `isShrinkResources=true`.

## 배포 전 꼭 해야 할 것 (콘솔/인프라)

### 1. ⬜ Firebase App Check 활성화 (가장 중요)
익명 인증이라 누구나 UID를 만들 수 있고 API 키는 APK에 들어간다. App Check(Play Integrity)로 **진짜 우리 앱**의 요청만 통과시킨다.
- Firebase 콘솔 → App Check → Play Integrity 등록
- 앱에 `firebase-appcheck-playintegrity` 의존성 + 초기화 추가
- Firestore·Storage·Auth 에서 App Check **강제(enforce)** 켜기

### 2. ⬜ Storage 클립 자동 삭제 (수명주기 규칙)
클라이언트 정리는 간병인이 앱을 열어야만 돈다. 서버에서 강제로 지운다.
```bash
gcloud storage buckets update gs://YOUR_STORAGE_BUCKET \
  --lifecycle-file=storage-lifecycle.json
```
(저장소의 `storage-lifecycle.json` 참고 — `clips/` 3일 후 자동 삭제)

### 3. ⬜ 릴리스 키스토어 생성 + 등록
```bash
keytool -genkey -v -keystore release.keystore -alias ansimdolbom \
  -keyalg RSA -keysize 2048 -validity 10000
```
- `local.properties`에 `RELEASE_STORE_FILE=release.keystore`, `RELEASE_STORE_PASSWORD=…`, `RELEASE_KEY_ALIAS=ansimdolbom`, `RELEASE_KEY_PASSWORD=…`
- **키스토어는 VCS·클라우드에 올리지 말 것** (분실 시 업데이트 불가)
- 이 키의 **SHA-1/키해시를 Kakao·Firebase 콘솔에 등록** (릴리스 빌드로 지도·인증되게)

### 4. ⬜ API 키 제한 (공개/배포 전)
Firebase API 키의 현재 API 제한과 앱 제한을 콘솔에서 확인한다.
- Google Cloud 콘솔 → 사용자 인증 정보 → 해당 키 → **Android 앱 제한**(패키지명 + 릴리스 SHA-1) + **API 제한**
- Kakao: 릴리스 키해시 등록 확인
- App Check(1번)가 실제 방어선이므로 반드시 병행

### 5. ⬜ 페어링 탈취 창 최소화 (선택, 권장)
현재 UUID QR을 아는 누구나(스캔 창에서) 환자로 등록 가능. UUIDv4라 추측은 불가하나:
- QR에 **만료 시간**을 넣거나, 간병인이 등록 UID를 **승인**하게 하면 더 안전

### 6. ⬜ 동의·중지 접근성 (규제/윤리)
- 최초 동의 화면은 명시적(유지). 환자가 자기 폰에서 **연결 해제** 가능하도록 유지.
- 상시 알림은 OS 마이크/카메라 표시등과 함께 유지(끄지 말 것).

## 참고 (현재 상태)
- `google-services.json`/카카오 키는 **저장소에 포함하지 않는다**(gitignore + `.example` 템플릿). 실제 키는 각자 본인 값을 로컬에 넣는다.
- Firebase API 키는 서버 비밀이 아니라 APK 에 포함되는 **클라이언트 식별자**다. 숨기는 것보다 **키 제한(패키지+SHA-1) + App Check** 로 악용을 막는 것이 실질 방어선이다.
- 배포 전 App Check 적용과 API 키 제한을 설정합니다.
- Cloud Function/FCM 은 추가 악용 경로 없음(요청 생성은 가족만, 토큰 읽기 불가, 전송은 Admin SDK).

## 연결 해제 보안 변경
연결 해제는 revoked 표시로 접근권을 즉시 차단하며 ID 재사용을 막기 위해 문서를 유지한다. Cloud Function cleanupRevokedPair가 요청·위치·토큰·클립을 삭제한다. 반드시 rules와 functions를 함께 배포해야 한다. 기존에 삭제된 연결의 잔존 자료는 관리자 권한으로 별도 삭제해야 한다. 오프라인 해제는 서버 반영을 보장하지 않으므로 온라인 상태에서 해제 완료를 확인한다.

## 접근권한 회귀 검사
Node 24.12 이상과 Firebase CLI, Java를 준비한 뒤 저장소 루트에서 실행한다. 실제 서버 대신 demo 에뮬레이터만 사용한다.

```bash
npm ci --prefix tests/security
firebase emulators:exec --only firestore --project demo-find-parent 'node tests/security/rules.test.cjs'
```

다른 사용자 조회 차단, 보호자의 결과 변조 차단, 환자의 연결 해제, 해제 후 조회 차단 및 ID 재사용 방지를 검증한다.
