package com.opendash.app.assistant.model

/**
 * Media attached to a user message for multimodal input.
 * Supported kinds depend on the active AssistantProvider (see ProviderCapabilities).
 *
 * Image content is passed as bytes or a file URI; audio/video similar.
 */
sealed class MediaAttachment {
    abstract val mimeType: String

    data class Image(
        override val mimeType: String,
        val bytes: ByteArray? = null,
        val uri: String? = null
    ) : MediaAttachment() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            if (mimeType != other.mimeType) return false
            if (uri != other.uri) return false
            if (bytes == null) return other.bytes == null
            if (other.bytes == null) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            result = 31 * result + (uri?.hashCode() ?: 0)
            return result
        }
    }

    data class Audio(
        override val mimeType: String,
        val bytes: ByteArray
    ) : MediaAttachment() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Audio) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
    }
}
