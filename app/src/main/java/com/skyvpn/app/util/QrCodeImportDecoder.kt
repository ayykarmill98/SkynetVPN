package com.skyvpn.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object QrCodeImportDecoder {

    suspend fun decodeFromUri(context: Context, uri: Uri): Result<String> = runCatching {
        decode(InputImage.fromFilePath(context, uri))
    }

    suspend fun decodeFromBitmap(bitmap: Bitmap): Result<String> = runCatching {
        decode(InputImage.fromBitmap(bitmap, 0))
    }

    private suspend fun decode(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        continuation.invokeOnCancellation {
            scanner.close()
        }

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qrText = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotBlank() }
                }
                scanner.close()

                if (!continuation.isActive) return@addOnSuccessListener
                if (qrText == null) {
                    continuation.resumeWithException(IllegalArgumentException("No QR code found"))
                } else {
                    continuation.resume(qrText)
                }
            }
            .addOnFailureListener { error ->
                scanner.close()
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
            .addOnCanceledListener {
                scanner.close()
                if (continuation.isActive) {
                    continuation.cancel(CancellationException("QR scan canceled"))
                }
            }
    }
}
