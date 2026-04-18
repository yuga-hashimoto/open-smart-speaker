package com.opendash.app.assistant.provider.embedded

import android.util.Base64
import com.opendash.app.assistant.model.MediaAttachment

/**
 * Encodes media attachments for inclusion in prompts.
 *
 * For LiteRT-LM multimodal we pass bytes through the Contents API
 * (the exact binding will land when we wire the multimodal engine).
 * Until then this helper produces a textual placeholder that preserves
 * context for non-vision models.
 */
object MediaEncoder {

    fun describeForTextModel(attachment: MediaAttachment): String = when (attachment) {
        is MediaAttachment.Image -> "[image:${attachment.mimeType}:${attachment.sizeLabel()}]"
        is MediaAttachment.Audio -> "[audio:${attachment.mimeType}:${attachment.bytes.size}B]"
    }

    /**
     * Base64 data URL for providers that accept one (OpenAI-compatible, etc.).
     */
    fun toDataUrl(attachment: MediaAttachment.Image): String? {
        val bytes = attachment.bytes ?: return null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:${attachment.mimeType};base64,$b64"
    }

    private fun MediaAttachment.Image.sizeLabel(): String {
        val size = bytes?.size ?: return uri ?: "uri-unknown"
        return "${size}B"
    }
}
