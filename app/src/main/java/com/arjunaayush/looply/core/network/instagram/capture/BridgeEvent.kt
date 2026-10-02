package com.arjunaayush.looply.core.network.instagram.capture

import org.json.JSONObject

sealed interface BridgeEvent {
    data class Payload(val url: String, val body: String) : BridgeEvent
    data class Template(val url: String) : BridgeEvent
    data class Page(val reqId: String, val status: Int, val body: String) : BridgeEvent
    data class Failure(val reqId: String?, val code: String, val message: String?) : BridgeEvent
    data class Navigated(val url: String) : BridgeEvent

    companion object {
        fun parse(raw: String): BridgeEvent? = runCatching {
            val o = JSONObject(raw)
            when (o.getString("type")) {
                "payload" -> Payload(o.optString("url"), o.optString("body"))
                "template" -> Template(o.optString("url"))
                "page" -> Page(o.getString("reqId"), o.optInt("status"), o.optString("body"))
                "failure" -> Failure(o.optString("reqId").ifBlank { null }, o.optString("code"), o.optString("message"))
                else -> null
            }
        }.getOrNull()
    }
}
