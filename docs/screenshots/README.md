# 앱 화면

API 35 Android 에뮬레이터의 밝은 테마에서 캡처한 Compose 화면입니다. 실제 계정 정보 대신 고정된 예시 데이터를 표시합니다.

- `guardian.png`: 간병인 홈
- `patient.png`: 환자 홈
- `setup.png`: 시작 안내

## 캡처

개발용 에뮬레이터를 부팅하고 로컬 빌드 설정을 준비한 뒤 실행합니다.

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.caregrandpapa.AppScreenshotsTest \
  com.caregrandpapa.test/androidx.test.runner.AndroidJUnitRunner
for screen in guardian patient setup; do
  adb exec-out run-as com.caregrandpapa cat "files/$screen.png" > "docs/screenshots/$screen.png"
done
```
