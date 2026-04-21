package com.example.ev.data

import android.content.Context
import android.net.Uri
import com.example.ev.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

data class UploadedImage(
    val url: String,
    val fileId: String
)

class ImageKitService(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun uploadScenarioImage(imageRef: String, scenarioId: Long): UploadedImage {
        if (imageRef.startsWith("http://") || imageRef.startsWith("https://")) {
            return UploadedImage(url = imageRef, fileId = "")
        }

        val publicKey = BuildConfig.IMAGEKIT_PUBLIC_KEY.trim()
        val authEndpoint = BuildConfig.IMAGEKIT_AUTH_ENDPOINT.trim()
        if (publicKey.isEmpty() || authEndpoint.isEmpty()) {
            throw IllegalStateException(
                "ImageKit is not configured. Set IMAGEKIT_PUBLIC_KEY and IMAGEKIT_AUTH_ENDPOINT."
            )
        }
        if (authEndpoint.contains("<region>") || authEndpoint.contains("<project-id>")) {
            throw IllegalStateException(
                "ImageKit endpoint is still a template. Set real IMAGEKIT_AUTH_ENDPOINT URL."
            )
        }

        val uri = Uri.parse(imageRef)
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw IllegalStateException("Cannot read image for upload")

        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "scenario_${scenarioId}_${System.currentTimeMillis()}.jpg"
        val auth = fetchUploadAuth(authEndpoint)

        return withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fileName", fileName)
                .addFormDataPart("publicKey", publicKey)
                .addFormDataPart("token", auth.token)
                .addFormDataPart("signature", auth.signature)
                .addFormDataPart("expire", auth.expire.toString())
                .addFormDataPart("useUniqueFileName", "true")
                .addFormDataPart("folder", "/ev/scenarios")
                .addFormDataPart(
                    "file",
                    fileName,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(IMAGEKIT_UPLOAD_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Image upload failed (${response.code}): $raw"
                    )
                }
                val json = JSONObject(raw)
                val url = json.optString("url")
                val fileId = json.optString("fileId")
                if (url.isBlank()) throw IllegalStateException("ImageKit response has no url")
                UploadedImage(url = url, fileId = fileId)
            }
        }
    }

    private suspend fun fetchUploadAuth(endpoint: String): ImageKitAuthData = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "ImageKit auth endpoint failed (${response.code}): $raw"
                )
            }
            val json = JSONObject(raw)
            val signature = json.optString("signature")
            val token = json.optString("token")
            val expire = json.optLong("expire")
            if (signature.isBlank() || token.isBlank() || expire <= 0L) {
                throw IllegalStateException("ImageKit auth endpoint returned invalid payload")
            }
            ImageKitAuthData(signature = signature, token = token, expire = expire)
        }
    }

    private data class ImageKitAuthData(
        val signature: String,
        val token: String,
        val expire: Long
    )

    private companion object {
        const val IMAGEKIT_UPLOAD_URL = "https://upload.imagekit.io/api/v1/files/upload"
    }
}
