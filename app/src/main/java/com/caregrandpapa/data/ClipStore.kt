package com.caregrandpapa.data

import android.content.Context
import android.util.Log
import java.io.File

/** 간병인 폰에 보관된 클립 하나. */
data class StoredClip(
    val requestId: String,
    val type: RequestType,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val file: File,
)

/**
 * 간병인 폰의 클립 보관소.
 *
 * 클라우드는 우편함일 뿐이고, 실제 보관은 이 폰 안에서 한다. 저장 위치는 앱 전용 폴더라
 * 갤러리에 노출되지 않고, 백업 규칙에서도 제외해 클라우드로 새어 나가지 않는다.
 *
 * 용량 상한은 [CAP_BYTES]. 넘으면 **오래된 것부터** 지운다. 시간 기준이 아니라 용량 기준인
 * 이유는, 요청을 많이 한 달에도 폰 용량이 예측 가능하게 유지되기 때문이다.
 *
 * 색인은 한 줄에 한 건인 단순 텍스트다. JSON 을 쓰면 안드로이드 `org.json` 에 묶여
 * JVM 단위 테스트에서 돌리기 어려워진다.
 */
class ClipStore(context: Context, pairId: String) {

    private val root = clipDirectory(context.filesDir, pairId)
    private val indexFile = File(root, INDEX_NAME)

    init {
        root.mkdirs()
    }

    /** 최신순 목록. */
    fun list(): List<StoredClip> = synchronized(LOCK) {
        readIndex()
            .mapNotNull { entry ->
                val file = File(root, entry.fileName)
                if (!file.exists()) return@mapNotNull null
                StoredClip(
                    requestId = entry.requestId,
                    type = entry.type,
                    createdAtMillis = entry.createdAtMillis,
                    sizeBytes = file.length(),
                    file = file,
                )
            }
            .sortedByDescending { it.createdAtMillis }
    }

    fun find(requestId: String): StoredClip? = list().firstOrNull { it.requestId == requestId }

    fun has(requestId: String): Boolean = find(requestId) != null

    /** 다운로드용 임시 파일 경로. 저장에 성공하면 [save] 가 정식 이름으로 옮긴다. */
    fun tempFileFor(requestId: String): File = File(root, "$requestId.part")

    /**
     * 내려받은 파일을 보관 목록에 넣는다.
     *
     * @return 보관된 클립, 실패하면 null
     */
    fun save(
        requestId: String,
        type: RequestType,
        createdAtMillis: Long,
        downloaded: File,
    ): StoredClip? {
        val extension = type.fileExtension ?: return null
        return synchronized(LOCK) {
            val fileName = "$requestId.$extension"
            val target = File(root, fileName)

            if (!downloaded.renameTo(target)) {
                // 같은 파일 시스템이라 보통 성공하지만, 실패하면 복사로 대체한다.
                runCatching { downloaded.copyTo(target, overwrite = true) }
                    .onFailure {
                        Log.e(TAG, "클립 저장 실패: ${it.message}", it)
                        return@synchronized null
                    }
                downloaded.delete()
            }

            val entries = readIndex().filterNot { it.requestId == requestId } +
                IndexEntry(requestId, type, createdAtMillis, fileName)
            writeIndex(entries)
            enforceCap()

            find(requestId)
        }
    }

    fun delete(requestId: String) = synchronized(LOCK) {
        val entries = readIndex()
        entries.firstOrNull { it.requestId == requestId }?.let { File(root, it.fileName).delete() }
        writeIndex(entries.filterNot { it.requestId == requestId })
    }

    fun totalBytes(): Long = list().sumOf { it.sizeBytes }

    /** 상한을 넘었으면 오래된 것부터 지운다. */
    fun enforceCap() = synchronized(LOCK) {
        val clips = list()
        val doomed = selectEvictions(
            clips.map { EvictionCandidate(it.requestId, it.createdAtMillis, it.sizeBytes) },
            CAP_BYTES,
        )
        if (doomed.isNotEmpty()) {
            Log.d(TAG, "용량 상한 초과 → ${doomed.size}건 정리")
            doomed.forEach { delete(it) }
        }
    }

    // ------------------------------------------------------------------ 색인

    private data class IndexEntry(
        val requestId: String,
        val type: RequestType,
        val createdAtMillis: Long,
        val fileName: String,
    )

    private fun readIndex(): List<IndexEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            indexFile.readLines().mapNotNull { parseIndexLine(it) }
        }.onFailure { Log.e(TAG, "색인 읽기 실패: ${it.message}", it) }
            .getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<IndexEntry>) {
        runCatching {
            indexFile.writeText(entries.joinToString("\n") { formatIndexLine(it) })
        }.onFailure { Log.e(TAG, "색인 쓰기 실패: ${it.message}", it) }
    }

    private fun parseIndexLine(line: String): IndexEntry? {
        val parts = line.trim().split(FIELD_SEPARATOR)
        if (parts.size != 4) return null
        val type = RequestType.fromWire(parts[1]) ?: return null
        val createdAt = parts[2].toLongOrNull() ?: return null
        if (parts[0].isBlank() || parts[3].isBlank()) return null
        return IndexEntry(parts[0], type, createdAt, parts[3])
    }

    private fun formatIndexLine(entry: IndexEntry): String = listOf(
        entry.requestId,
        entry.type.wireName,
        entry.createdAtMillis.toString(),
        entry.fileName,
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        private const val TAG = "ClipStore"
        private const val DIR_NAME = "clips"
        private const val INDEX_NAME = "index.txt"
        private const val FIELD_SEPARATOR = "|"

        // 색인 파일은 앱 전체에서 하나뿐이므로, 인스턴스가 여러 개여도 이 락으로 직렬화한다.
        private val LOCK = Any()

        /** 보관 용량 상한 1GB. 15초 영상이 약 2.8MB 이므로 350건 이상 들어간다. */
        const val CAP_BYTES = 1_024L * 1024L * 1024L
    }
}

/** [selectEvictions] 입력. 파일 시스템과 무관하게 만들어 테스트할 수 있다. */
data class EvictionCandidate(
    val requestId: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
)

/**
 * 용량 상한을 지키기 위해 지울 항목을 고른다.
 *
 * 오래된 것부터 지우고, 합계가 상한 이하로 떨어지면 멈춘다.
 * 상한보다 큰 파일 하나만 있는 극단적인 경우에도 최소 한 건은 남긴다 —
 * 방금 받은 것을 곧바로 지워 버리면 사용자가 아무것도 못 보게 되기 때문이다.
 */
fun selectEvictions(candidates: List<EvictionCandidate>, capBytes: Long): List<String> {
    var total = candidates.sumOf { it.sizeBytes }
    if (total <= capBytes) return emptyList()

    val oldestFirst = candidates.sortedBy { it.createdAtMillis }
    val doomed = mutableListOf<String>()
    for (candidate in oldestFirst) {
        if (total <= capBytes) break
        if (doomed.size == candidates.size - 1) break // 마지막 한 건은 남긴다
        doomed += candidate.requestId
        total -= candidate.sizeBytes
    }
    return doomed
}

/** A stable per-pair directory; hashing keeps arbitrary IDs out of filesystem paths. */
internal fun clipDirectory(filesDir: File, pairId: String): File {
    require(pairId.isNotBlank())
    val key = java.security.MessageDigest.getInstance("SHA-256")
        .digest(pairId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return File(filesDir, "clips/pairs/$key")
}
