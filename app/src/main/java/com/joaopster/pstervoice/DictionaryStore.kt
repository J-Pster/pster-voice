package com.joaopster.pstervoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object DictionaryStore {

    private const val PREFS_NAME = "pster_voice_prefs"
    private const val KEY_ENTRIES = "dictionary_entries"

    // wrong == null (ou vazio) significa "keyterm" (dica de vocabulario pro ElevenLabs),
    // nao um par de substituicao de texto.
    data class Entry(val wrong: String?, val correct: String)

    fun loadEntries(context: Context): List<Entry> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val wrong = obj.optString("wrong", "").ifBlank { null }
                Entry(wrong, obj.getString("correct"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun saveEntries(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("wrong", entry.wrong ?: "")
                    put("correct", entry.correct)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }

    fun applyDictionary(text: String, entries: List<Entry>): String {
        var result = text
        entries.forEach { entry ->
            val wrong = entry.wrong
            if (!wrong.isNullOrBlank()) {
                result = result.replace(wrong, entry.correct, ignoreCase = true)
            }
        }
        return result
    }

    fun getKeyterms(entries: List<Entry>): List<String> =
        entries.filter { it.wrong.isNullOrBlank() }.map { it.correct }
}
