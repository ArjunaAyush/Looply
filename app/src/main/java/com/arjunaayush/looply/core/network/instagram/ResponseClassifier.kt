package com.arjunaayush.looply.core.network.instagram

enum class ResponseVerdict { OK, RATE_LIMITED, CHALLENGE, SESSION_EXPIRED, TRANSIENT }

object ResponseClassifier {
    fun classify(status: Int, body: String): ResponseVerdict = when {
        status == 429 -> ResponseVerdict.RATE_LIMITED
        "checkpoint_required" in body || "challenge_required" in body -> ResponseVerdict.CHALLENGE
        status == 401 || "login_required" in body -> ResponseVerdict.SESSION_EXPIRED
        status == 403 -> ResponseVerdict.RATE_LIMITED       // treat as "back off hard", never retry immediately
        "feedback_required" in body || "Please wait a few minutes" in body -> ResponseVerdict.RATE_LIMITED
        status !in 200..299 -> ResponseVerdict.TRANSIENT
        else -> ResponseVerdict.OK
    }
}
