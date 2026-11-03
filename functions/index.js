/**
 * 보호자가 요청 버튼을 누르면 아버님 폰을 FCM 으로 깨우는 Cloud Function.
 *
 * 흐름:
 *   보호자 앱이 pairs/{pairId}/requests/{requestId} 를 만듦 (status: pending)
 *   → 이 함수가 트리거되어 tokens/{pairId}.token 으로 데이터 메시지 전송
 *   → 아버님 폰의 WakeMessagingService 가 깨어나 MonitoringService 시작
 *   → 서비스가 pending 요청을 집어가 15초 녹음/녹화 또는 위치 측정을 수행
 *
 * 평소에는 아버님 폰의 Foreground Service 가 Firestore 를 구독하고 있어 이 푸시 없이도
 * 요청이 즉시 전달된다. 이 함수는 그 구독이 죽었을 때(배터리 최적화로 종료, 앱 강제 종료)의
 * 복구 수단이다.
 *
 * 배포:  firebase deploy --only functions   (Blaze 요금제 필요)
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.wakeOnRequest = onDocumentCreated(
  "pairs/{pairId}/requests/{requestId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    // 보호자가 만든 새 요청만 대상. 상태 갱신은 onDocumentCreated 로 오지 않는다.
    if (data.status !== "pending") return;

    const { pairId, requestId } = event.params;
    const pair = await getFirestore().collection("pairs").doc(pairId).get();
    if (!pair.exists || pair.get("revoked") === true) return;
    const tokenSnap = await getFirestore().collection("tokens").doc(pairId).get();
    const token = tokenSnap.get("token");
    if (!token) {
      console.log(`토큰 없음: ${pairId}`);
      return;
    }

    await getMessaging().send({
      token,
      data: { type: "wake", pairId, requestId, requestType: String(data.type ?? "") },
      android: { priority: "high" },
    });
    console.log(`wake 전송 완료: ${pairId} (${data.type} / ${requestId})`);
  },
);

// Retry cleanup on transient failures. Never delete the parent tombstone.
exports.cleanupRevokedPair = onDocumentUpdated(
  { document: "pairs/{pairId}", retry: true },
  async (event) => {
    if (event.data?.after.get("revoked") !== true || event.data.after.get("cleanupComplete") === true) return;
    const db = getFirestore();
    const pairId = event.params.pairId;
    const { getStorage } = require("firebase-admin/storage");
    await getStorage().bucket().deleteFiles({ prefix: `clips/${pairId}/` });
    await db.recursiveDelete(db.collection("pairs").doc(pairId).collection("requests"));
    await db.collection("locations").doc(pairId).delete();
    await db.collection("tokens").doc(pairId).delete();
    const { FieldValue } = require("firebase-admin/firestore");
    await db.collection("pairs").doc(pairId).update({
      checkIn: FieldValue.delete(), checkInAt: FieldValue.delete(),
      batteryPercent: FieldValue.delete(), charging: FieldValue.delete(),
      lastSeenAt: FieldValue.delete(), cleanupComplete: true,
    });
  },
);
