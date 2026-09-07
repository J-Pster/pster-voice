package com.joaopster.pstervoice

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiCleanupClient {

    private const val TAG = "PsterVoice"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    private const val SYSTEM_PROMPT = """
Você recebe uma transcrição bruta de fala em português, com erros típicos de ditado por voz. Ajuste o texto seguindo estas regras, sem adicionar informação nova nem mudar o significado:
1. Adicione pontuação apropriada (pontos, vírgulas, interrogação, exclamação) onde fizer sentido pela entonação/estrutura da frase.
2. Se a pessoa disser explicitamente um comando de formatação como "quebra linha", "nova linha", "break line" ou similar, EXECUTE o comando (insira uma quebra de linha real) em vez de escrever essas palavras no texto.
3. Se a pessoa disser explicitamente "ponto", "vírgula", "ponto de interrogação", "ponto de exclamação" como comando de pontuação (não como parte do conteúdo falado), substitua pelo símbolo correspondente.
4. Remova repetições que são claramente gagueira ou hesitação não intencional (ex: "testando, testando, testando" sem contexto que indique propósito) — mas PRESERVE repetições que parecem intencionais ou enfáticas (ex: quando a pessoa claramente quer enfatizar algo repetindo).
5. Deixe o texto mais conciso e limpo quando possível, sem perder a essência, o tom nem o significado original.
6. Não invente conteúdo, não resuma demais, não mude o idioma. Responda APENAS com o texto final ajustado, sem explicações, sem aspas, sem comentários.
"""

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun cleanup(rawText: String): String {
        return try {
            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", rawText)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 500)
                })
            }

            val request = Request.Builder()
                .url("$ENDPOINT?key=${BuildConfig.GEMINI_API_KEY}")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Resposta Gemini cleanup: HTTP ${response.code}")
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gemini cleanup falhou HTTP ${response.code}: ${response.body?.string()}")
                    return rawText
                }

                val bodyString = response.body?.string()
                if (bodyString == null) {
                    Log.w(TAG, "Gemini cleanup: corpo de resposta vazio")
                    return rawText
                }

                val cleaned = JSONObject(bodyString)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                Log.d(TAG, "Texto limpo pelo Gemini: \"$cleaned\"")
                cleaned.ifBlank { rawText }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Gemini cleanup falhou (rede/timeout), usando texto bruto", e)
            rawText
        } catch (e: JSONException) {
            Log.w(TAG, "Gemini cleanup falhou (parsing), usando texto bruto", e)
            rawText
        }
    }
}
