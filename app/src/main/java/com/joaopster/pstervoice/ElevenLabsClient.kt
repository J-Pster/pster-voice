package com.joaopster.pstervoice

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ElevenLabsClient {

    private const val TAG = "PsterVoice"
    private const val MAX_KEYTERM_LENGTH = 50
    private const val MAX_KEYTERM_COUNT = 1000

    sealed class TranscriptionResult {
        data class Success(val text: String) : TranscriptionResult()
        object NoSpeech : TranscriptionResult()
        object Failure : TranscriptionResult()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(wavFile: File, keyterms: List<String> = emptyList()): TranscriptionResult {
        return try {
            Log.d(TAG, "Enviando arquivo WAV: ${wavFile.absolutePath}, tamanho=${wavFile.length()} bytes")
            if (wavFile.length() < 1000) {
                Log.w(TAG, "Arquivo de audio suspeito, pode ser curto demais para a API (minimo 100ms)")
            }

            val limitedKeyterms = keyterms
                .map { if (it.length > MAX_KEYTERM_LENGTH) it.take(MAX_KEYTERM_LENGTH) else it }
                .take(MAX_KEYTERM_COUNT)

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model_id", "scribe_v2")
                .addFormDataPart("file", wavFile.name, wavFile.asRequestBody("audio/wav".toMediaType()))

            if (limitedKeyterms.isNotEmpty()) {
                bodyBuilder.addFormDataPart("keyterms", JSONArray(limitedKeyterms).toString())
                Log.d(TAG, "Keyterms enviados (${limitedKeyterms.size}): $limitedKeyterms")
            }

            val requestBody = bodyBuilder.build()

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/speech-to-text")
                .addHeader("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Resposta ElevenLabs: HTTP ${response.code}")
                val bodyString = response.body?.string()
                Log.d(TAG, "Corpo bruto da resposta: $bodyString")

                if (!response.isSuccessful) {
                    Log.e(TAG, "ElevenLabs retornou erro ${response.code}: $bodyString")
                    return TranscriptionResult.Failure
                }
                if (bodyString == null) {
                    Log.e(TAG, "Corpo de resposta vazio apesar de HTTP ${response.code}")
                    return TranscriptionResult.Failure
                }

                val json = JSONObject(bodyString)
                val text = if (json.has("text")) json.getString("text") else null
                Log.d(TAG, "Texto transcrito: \"$text\"")

                if (text.isNullOrBlank()) TranscriptionResult.NoSpeech else TranscriptionResult.Success(text)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao transcrever", e)
            TranscriptionResult.Failure
        } catch (e: JSONException) {
            Log.e(TAG, "Erro ao transcrever", e)
            TranscriptionResult.Failure
        } finally {
            wavFile.delete()
        }
    }
}
