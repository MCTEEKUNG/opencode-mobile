package dev.opencode.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.Base64

object AttachmentFiles {
    suspend fun bytes(context: Context, file: ChatAttachment, computer: Computer?, directory: String): ByteArray = withContext(Dispatchers.IO) {
        if (file.uri.startsWith("data:")) {
            val marker = file.uri.indexOf(";base64,")
            require(marker > 0) { "Unsupported file encoding." }
            val encoded = file.uri.substring(marker + 8)
            require(encoded.length <= (ChatAttachment.MAX_BYTES.toLong() * 4 / 3 + 4)) { "This file is too large to preview." }
            return@withContext Base64.getDecoder().decode(encoded)
        }
        val request = attachmentRequest(file, computer, directory)
        connectionTransport(context, request.url).newCall(request).execute().use { response ->
            require(response.isSuccessful) { "File could not be loaded (HTTP ${response.code})." }
            val body = response.body ?: error("The file is empty.")
            require(body.contentLength() <= ChatAttachment.MAX_BYTES) { "This file is too large to preview." }
            body.byteStream().use(::readAttachmentBytes)
        }
    }
    suspend fun bitmap(context: Context, file: ChatAttachment, computer: Computer?, directory: String, maxSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = bytes(context, file, computer, directory)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > maxSize || bounds.outHeight / options.inSampleSize > maxSize) options.inSampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
    suspend fun open(context: Context, file: ChatAttachment, computer: Computer?, directory: String) {
        val bytes = bytes(context, file, computer, directory)
        val uri = withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "attachments").apply { mkdirs() }
            // Names are display metadata, never trusted filesystem paths.
            val extension = file.name.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "bin"
            val target = File.createTempFile("attachment-", ".$extension", folder)
            target.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(Intent.createChooser(intent, "Open ${file.name}")) }
        catch (_: android.content.ActivityNotFoundException) { error("No installed app can open this file type.") }
    }
}
