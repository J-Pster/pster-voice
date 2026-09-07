package com.joaopster.pstervoice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.joaopster.pstervoice.databinding.ActivityDictionaryBinding
import com.joaopster.pstervoice.databinding.ItemDictionaryEntryBinding

class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private val entries = mutableListOf<DictionaryStore.Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        entries.addAll(DictionaryStore.loadEntries(this))
        renderEntries()

        binding.buttonAddEntry.setOnClickListener {
            val wrong = binding.inputWrong.text.toString().trim().ifEmpty { null }
            val correct = binding.inputCorrect.text.toString().trim()
            if (correct.isNotEmpty()) {
                entries.add(DictionaryStore.Entry(wrong, correct))
                DictionaryStore.saveEntries(this, entries)
                binding.inputWrong.text.clear()
                binding.inputCorrect.text.clear()
                renderEntries()
            }
        }
    }

    private fun renderEntries() {
        binding.entriesContainer.removeAllViews()
        entries.forEachIndexed { index, entry ->
            val itemBinding = ItemDictionaryEntryBinding.inflate(layoutInflater, binding.entriesContainer, false)
            itemBinding.textEntry.text = if (entry.wrong.isNullOrBlank()) {
                entry.correct
            } else {
                getString(R.string.dictionary_entry_format, entry.wrong, entry.correct)
            }
            itemBinding.buttonRemove.setOnClickListener {
                entries.removeAt(index)
                DictionaryStore.saveEntries(this, entries)
                renderEntries()
            }
            binding.entriesContainer.addView(itemBinding.root)
        }
    }
}
