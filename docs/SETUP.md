# care_grandpapa 설정과 검증

Android 휴대폰 두 대에서 가족과 당사자를 연결한다. 요청할 때만 15초 녹음·녹화 또는 위치 측정을 수행한다. 당사자의 명시적 동의를 받은 환경에서 사용한다.

## 개발 환경

- Android Studio와 프로젝트의 compileSdk에 맞는 Android SDK
- Gradle Wrapper가 선택하는 JDK
- Cloud Functions: Node 20, Firebase CLI
- 보안 테스트: Node 24.12 이상과 Java

SDK를 찾지 못하면 로컬 `local.properties`에 `sdk.dir`을 설정하거나 `ANDROID_HOME`을 지정한다. 해당 파일은 커밋하지 않는다.

## 서비스 설정

1. 본인 Firebase 프로젝트에서 Authentication 익명 로그인, Firestore, Storage를 활성화한다. 데이터베이스는 프로덕션 모드로 생성한다.
2. Firebase 콘솔에서 Android 앱 설정 파일을 받아 `app/google-services.json`에 둔다. 예제 파일만으로는 실제 서버를 사용할 수 없다.
3. `secrets.properties.example`을 `secrets.properties`로 복사하고 카카오 네이티브 앱 키를 입력한다. 카카오 콘솔에 패키지명과 서명 키 해시를 등록한다.
4. `.firebaserc.example`을 `.firebaserc`로 복사하고 본인의 프로젝트 ID를 입력한다.
5. Firebase App Check를 설정한다. debug는 디버그 토큰을 콘솔에 등록하고, release는 Play Integrity와 해당 배포 방식의 호환성을 확인한다. 서비스별 enforcement 상태도 확인한다.
6. Firebase CLI로 로그인한 후 다음을 실행한다.

```bash
npm ci --prefix functions
firebase deploy --only firestore:rules,storage,functions
```

연결 해제 정리 함수까지 함께 배포해야 한다. 연결 해제는 먼저 접근권한을 취소하고, 서버 함수가 요청 문서·클립·위치·토큰을 삭제한다. 연결 ID 재사용 방지 문서는 유지한다. 과거 버전에서 이미 삭제된 연결의 잔존 자료는 관리자 확인 후 별도로 정리해야 한다.

Storage 보관 기간을 제한하려면 `storage-lifecycle.json`의 안내에 따라 본인 버킷에 수명주기 설정을 별도로 적용한다. 파일을 저장소에 넣는 것만으로 적용되지는 않는다.

## 빌드와 테스트

```bash
./gradlew testDebugUnitTest lintDebug
./gradlew installDebug
npm ci --prefix tests/security
firebase emulators:exec --only firestore --project demo-find-parent 'node tests/security/rules.test.cjs'
```

에뮬레이터 테스트는 실제 운영 자료를 사용하지 않는다. 실기기 확인은 별도로 필요하다.

## 두 휴대폰 연결

1. 가족 폰에서 보호자 역할을 선택하고 QR을 표시한다.
2. 당사자 폰에서 QR을 스캔하고 동의 내용을 확인한다.
3. 필요한 권한을 허용한다. 위치의 백그라운드 권한과 배터리 제한은 기기 설정에서 확인한다.
4. 가족 폰에서 소리·전면·후면·위치를 각각 요청한다.
5. 당사자 홈에서 현재 상태와 공유 중지·재개 버튼을 확인한다.

공유 중지는 저장되며 앱 재진입·재부팅·원격 깨우기로 자동 해제되지 않는다. 당사자가 재개해야 다시 요청을 받는다. 연결 해제에 실패하면 온라인에서 다시 시도한다.

## 실기기 회귀 확인

- 권한 거부·철회 시 강제 종료 없이 설명을 표시하는지
- 공유 중지 후 앱 재진입·재부팅·원격 요청에도 중지 상태를 유지하는지
- 녹음·녹화 중 중지했을 때 작업이 종료되는지
- 네트워크 단절 후 연결 해제 실패를 알리고 재시도할 수 있는지
- 화면 회전 후 역할·연결 상태가 유지되는지
- 다른 연결의 기록이 목록이나 재생 화면에 섞이지 않는지
- 연결 해제 후 서버 자료 접근이 거부되고 정리 함수가 완료되는지

이전 버전의 공용 로컬 기록은 자동으로 새 연결에 귀속하지 않는다. 파일은 보존되지만 새 기록 목록에는 표시되지 않는다.

## 안부·기기 상태 기능 활성화

신규 앱 설치 전에 수정된 Firestore 규칙과 `cleanupRevokedPair` 함수를 함께 배포한다. 기존 규칙에는 안부·기기 상태 필드 쓰기 권한이 없으므로 앱만 업데이트하면 전송이 거부된다.

당사자 폰의 버튼 두 종류를 보내 가족 화면에 최신 안부가 표시되는지 확인하고, 공유 켜짐 상태에서 배터리·응답 시각이 갱신되는지 확인한다. 공유 중지 후에는 자동 갱신이 멈춰야 한다. 도움 요청은 앱 내 표시이며 푸시 알림은 제공하지 않는다.
