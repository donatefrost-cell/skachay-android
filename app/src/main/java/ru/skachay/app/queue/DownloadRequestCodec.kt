package ru.skachay.app.queue

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import ru.skachay.app.DownloadService

internal object DownloadRequestCodec {
    private const val MAX_PAYLOAD_CHARS = 64_000

    fun encode(intent: Intent): String {
        val extras = JSONObject()
        intent.extras?.keySet()?.asSequence()?.filter { it != DownloadService.EXTRA_NOTIFICATION_ID }?.sorted()?.forEach { key ->
            val value = intent.extras?.get(key) ?: return@forEach
            val typed =
                when (value) {
                    is String -> {
                        JSONObject().put("type", "string").put("value", value)
                    }

                    is CharSequence -> {
                        JSONObject().put("type", "string").put("value", value.toString())
                    }

                    is Int -> {
                        JSONObject().put("type", "int").put("value", value)
                    }

                    is Long -> {
                        JSONObject().put("type", "long").put("value", value)
                    }

                    is Boolean -> {
                        JSONObject().put("type", "boolean").put("value", value)
                    }

                    is IntArray -> {
                        JSONObject().put("type", "int_array").put("value", JSONArray(value.toList()))
                    }

                    is ArrayList<*> -> {
                        val strings = value.filterIsInstance<String>()
                        if (strings.size != value.size) null else JSONObject().put("type", "string_list").put("value", JSONArray(strings))
                    }

                    else -> {
                        null
                    }
                }
            if (typed != null) extras.put(key, typed)
        }
        val encoded = JSONObject().put("schema", 1).put("extras", extras).toString()
        require(encoded.length <= MAX_PAYLOAD_CHARS) { "Download request is too large to persist" }
        return encoded
    }

    fun restore(
        context: Context,
        payload: String,
        id: Int,
    ): Intent {
        require(payload.length <= MAX_PAYLOAD_CHARS)
        val root = JSONObject(payload)
        require(root.optInt("schema") == 1)
        val extras = root.getJSONObject("extras")
        val intent = Intent(context, DownloadService::class.java)
        val keys = extras.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val typed = extras.getJSONObject(key)
            when (typed.getString("type")) {
                "string" -> intent.putExtra(key, typed.getString("value"))
                "int" -> intent.putExtra(key, typed.getInt("value"))
                "long" -> intent.putExtra(key, typed.getLong("value"))
                "boolean" -> intent.putExtra(key, typed.getBoolean("value"))
                "int_array" -> intent.putExtra(key, typed.getJSONArray("value").toIntArray())
                "string_list" -> intent.putStringArrayListExtra(key, typed.getJSONArray("value").toStringList())
                else -> throw IllegalArgumentException("Unsupported persisted download request")
            }
        }
        return intent.putExtra(DownloadService.EXTRA_NOTIFICATION_ID, id)
    }

    private fun JSONArray.toIntArray(): IntArray = IntArray(length()) { index -> getInt(index) }

    private fun JSONArray.toStringList(): ArrayList<String> =
        ArrayList<String>(length()).apply {
            for (index in 0 until this@toStringList.length()) add(getString(index))
        }
}
