package com.arjunaayush.looply.utils

object Constants {
    // Storage
    const val VIDEOS_DIRECTORY_NAME = "videos"
    const val LEGACY_VIDEO_FILE_NAME = "looply_saved_video.mp4"

    // Supported Video Formats
    val SUPPORTED_VIDEO_EXTENSIONS = listOf("mp4", "mkv", "mov", "webm", "3gp")

    // MIME Types & Intents
    const val MIME_TYPE_VIDEO_ALL = "video/*"
    const val REQUEST_CODE_PICK_VIDEO = 100

    // Animation & Feedback
    const val TAP_PAUSE_INDICATOR_DURATION_MS = 600L
}
