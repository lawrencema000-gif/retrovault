package com.retrovault.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DownloadState { QUEUED, DOWNLOADING, INSTALLED, FAILED, PAUSED }

/** Response from the get-download-url Edge Function. */
@Serializable
data class SignedDownload(
    val url: String,
    val sha256: String? = null,
    val size: Long = 0,
    @SerialName("file_name") val fileName: String? = null,
)
