package br.unasp.boacao.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    /** Creates a temp file URI via FileProvider for TakePicture contract (avoids TakePicturePreview freeze). */
    fun createTempImageUri(context: Context): Uri {
        val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap
        val ratioX = maxWidth.toFloat() / width
        val ratioY = maxHeight.toFloat() / height
        val ratio = minOf(ratioX, ratioY)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 50): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** Converte URI de imagem em Base64 comprimido. maxWidth/maxHeight em pixels. */
    fun uriToBase64(context: Context, uri: Uri, maxWidth: Int = 400, maxHeight: Int = 400, quality: Int = 50): String? {
        val bitmap = uriToBitmap(context, uri) ?: return null
        val resized = resizeBitmap(bitmap, maxWidth, maxHeight)
        return bitmapToBase64(resized, quality)
    }
}
