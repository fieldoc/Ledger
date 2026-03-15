package com.example.todowallapp.capture.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

data class PendingCaptureRecord(
    val id: String,
    val filePath: String,
    val createdAtEpochMs: Long,
    val lastError: String? = null
)

class PendingCaptureStore(
    context: Context,
    private val gson: Gson = Gson()
) {
    private val appContext = context.applicationContext
    private val metadataPreferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val captureDirectory: File = File(appContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    fun listPendingCaptures(): List<PendingCaptureRecord> {
        return readMetadata().sortedByDescending { it.createdAtEpochMs }
    }

    fun saveCapture(imageBytes: ByteArray): Result<PendingCaptureRecord> = runCatching {
        val id = UUID.randomUUID().toString()
        val file = File(captureDirectory, "$id.jpg")
        file.writeBytes(imageBytes)

        val record = PendingCaptureRecord(
            id = id,
            filePath = file.absolutePath,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val updated = readMetadata().toMutableList().apply { add(record) }
        writeMetadata(updated)
        record
    }

    fun readCaptureBytes(recordId: String): Result<ByteArray> = runCatching {
        val record = readMetadata().firstOrNull { it.id == recordId }
            ?: error("Pending capture not found")
        File(record.filePath).readBytes()
    }

    fun removeCapture(recordId: String) {
        val records = readMetadata().toMutableList()
        val index = records.indexOfFirst { it.id == recordId }
        if (index < 0) return

        val removed = records.removeAt(index)
        File(removed.filePath).takeIf { it.exists() }?.delete()
        writeMetadata(records)
    }

    fun updateError(recordId: String, message: String?) {
        val updated = readMetadata().map { record ->
            if (record.id == recordId) {
                record.copy(lastError = message)
            } else {
                record
            }
        }
        writeMetadata(updated)
    }

    private fun readMetadata(): List<PendingCaptureRecord> {
        val raw = metadataPreferences.getString(KEY_PENDING_METADATA, null) ?: return emptyList()
        return runCatching {
            val listType = object : TypeToken<List<PendingCaptureRecord>>() {}.type
            gson.fromJson<List<PendingCaptureRecord>>(raw, listType).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeMetadata(records: List<PendingCaptureRecord>) {
        metadataPreferences.edit()
            .putString(KEY_PENDING_METADATA, gson.toJson(records))
            .apply()
    }

    companion object {
        private const val PREF_NAME = "pending_capture_store"
        private const val KEY_PENDING_METADATA = "pending_metadata"
        private const val DIRECTORY_NAME = "pending_captures"
    }
}
