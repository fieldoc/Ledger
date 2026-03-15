package com.example.todowallapp.capture.repository

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class ScannerAvailability(
    val isAvailable: Boolean,
    val message: String? = null,
    val canResolveInUi: Boolean = false
)

sealed class ScanCaptureResult {
    data class Success(val imageUri: Uri) : ScanCaptureResult()
    object Cancelled : ScanCaptureResult()
    data class Error(val message: String) : ScanCaptureResult()
}

class ScannerRepository(
    private val scannerOptions: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
) {
    private val scannerClient = GmsDocumentScanning.getClient(scannerOptions)

    fun checkPlayServicesAvailability(activity: Activity): ScannerAvailability {
        val availability = GoogleApiAvailability.getInstance()
        val statusCode = availability.isGooglePlayServicesAvailable(activity)
        if (statusCode == ConnectionResult.SUCCESS) {
            return ScannerAvailability(isAvailable = true)
        }

        val isResolvable = availability.isUserResolvableError(statusCode)
        val message = when (statusCode) {
            ConnectionResult.SERVICE_MISSING -> "Google Play Services is missing"
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Google Play Services needs an update"
            ConnectionResult.SERVICE_DISABLED -> "Google Play Services is disabled"
            else -> "Google Play Services is unavailable"
        }
        return ScannerAvailability(
            isAvailable = false,
            message = message,
            canResolveInUi = isResolvable
        )
    }

    fun showPlayServicesResolutionDialog(activity: Activity) {
        val availability = GoogleApiAvailability.getInstance()
        val statusCode = availability.isGooglePlayServicesAvailable(activity)
        if (availability.isUserResolvableError(statusCode)) {
            availability.getErrorDialog(activity, statusCode, PLAY_SERVICES_REQUEST_CODE)?.show()
        }
    }

    suspend fun createScanIntentSender(activity: Activity): Result<IntentSenderRequest> {
        return suspendCancellableCoroutine { continuation ->
            scannerClient.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    continuation.resume(
                        Result.success(IntentSenderRequest.Builder(intentSender).build())
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error))
                }
        }
    }

    fun parseActivityResult(resultCode: Int, data: Intent?): ScanCaptureResult {
        if (resultCode != Activity.RESULT_OK) {
            return ScanCaptureResult.Cancelled
        }

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
            ?: return ScanCaptureResult.Error("Scanner did not return a document")
        val imageUri = scanResult.pages?.firstOrNull()?.imageUri
            ?: return ScanCaptureResult.Error("Scanner result contained no image")

        return ScanCaptureResult.Success(imageUri)
    }

    suspend fun readAndCompressJpeg(
        activity: Activity,
        uri: Uri,
        maxDimension: Int = 1600,
        quality: Int = 85
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = activity.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val sourceWidth = boundsOptions.outWidth
            val sourceHeight = boundsOptions.outHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                error("Unable to decode scanned image metadata")
            }

            var sampleSize = 1
            while (
                sourceWidth / sampleSize > maxDimension ||
                sourceHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }

            val bitmap = resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: error("Unable to decode scanned image")

            ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
                bitmap.recycle()
                output.toByteArray()
            }
        }
    }

    companion object {
        private const val PLAY_SERVICES_REQUEST_CODE = 4012
    }
}
