package com.arjunaayush.looply.core.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IngestionLogger {
    private const val TAG_PREFIX = "LooplyIngestion"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun log(tag: String, message: String) {
        val time = timeFormat.format(Date())
        val entry = "[$time][$tag] $message"
        Log.d("$TAG_PREFIX:$tag", message)
        val current = _logs.value
        _logs.value = if (current.size >= 200) {
            current.drop(current.size - 199) + entry
        } else {
            current + entry
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
