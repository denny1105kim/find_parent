package com.caregrandpapa.data

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * 녹음·녹화 파일이 지나가는 임시 우편함(Cloud Storage).
 *
 * 파일은 여기 오래 머물지 않는다. 환자 폰이 올리고, 간병인 폰이 내려받아 자기 폰에
 * 보관한 뒤([ClipStore]) 서버 원본을 지운다. 정상 흐름에서는 몇 분만 존재한다.
 */
object ClipStorage {

    private const val TAG = "ClipStorage"

    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    /** `clips/{pairId}/{requestId}.{ext}` */
    fun pathFor(pairId: String, requestId: String, extension: String): String =
        "clips/$pairId/$requestId.$extension"

    fun contentTypeFor(type: RequestType): String =
        if (type.isVideo) "video/mp4" else "audio/mp4"

    /** 환자: 파일을 올리고 업로드된 바이트 수를 돌려준다. */
    suspend fun upload(path: String, file: File, contentType: String): Long {
        Auth.awaitUid()
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        storage.reference.child(path).putFile(Uri.fromFile(file), metadata).await()
        return file.length()
    }

    /** 간병인: 파일을 내려받는다. 실패하면 예외를 던진다. */
    suspend fun download(path: String, destination: File) {
        Auth.awaitUid()
        destination.parentFile?.mkdirs()
        storage.reference.child(path).getFile(destination).await()
    }

    /** 간병인: 받아간 원본을 지운다. 이미 없으면 조용히 넘어간다. */
    suspend fun delete(path: String) {
        Auth.awaitUid()
        try {
            storage.reference.child(path).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    /**
     * 연결 해제 시: 이 페어의 클립 폴더(`clips/{pairId}/`)에 남은 파일을 모두 지운다.
     * **반드시 pairs 문서를 지우기 전에** 호출해야 한다. 문서가 사라지면 보안 규칙상
     * 이 파일들에 대한 권한이 없어져 영구히 고아 파일로 남는다.
     */
    suspend fun deleteAllForPair(pairId: String) {
        runCatching {
            Auth.awaitUid()
            val folder = storage.reference.child("clips/$pairId")
            val listing = folder.listAll().await()
            listing.items.forEach { item ->
                runCatching { item.delete().await() }
                    .onFailure { Log.w(TAG, "클립 삭제 실패(${item.path}): ${it.message}") }
            }
        }.onFailure { Log.w(TAG, "클립 폴더 정리 실패($pairId): ${it.message}") }
    }
}
