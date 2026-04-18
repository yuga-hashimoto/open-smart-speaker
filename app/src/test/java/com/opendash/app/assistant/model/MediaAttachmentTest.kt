package com.opendash.app.assistant.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MediaAttachmentTest {

    @Test
    fun `two Image attachments with same bytes are equal`() {
        val a = MediaAttachment.Image("image/png", bytes = byteArrayOf(1, 2, 3))
        val b = MediaAttachment.Image("image/png", bytes = byteArrayOf(1, 2, 3))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `different bytes produce different equality`() {
        val a = MediaAttachment.Image("image/png", bytes = byteArrayOf(1, 2, 3))
        val b = MediaAttachment.Image("image/png", bytes = byteArrayOf(1, 2, 4))

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `Image with URI-only is equal when URIs match`() {
        val a = MediaAttachment.Image("image/jpeg", uri = "content://a/1")
        val b = MediaAttachment.Image("image/jpeg", uri = "content://a/1")

        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `Audio equality is based on bytes`() {
        val a = MediaAttachment.Audio("audio/wav", byteArrayOf(9, 9))
        val b = MediaAttachment.Audio("audio/wav", byteArrayOf(9, 9))

        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `User message accepts attachments`() {
        val attach = MediaAttachment.Image("image/png", bytes = byteArrayOf(1))
        val msg = AssistantMessage.User(content = "look at this", attachments = listOf(attach))

        assertThat(msg.attachments).hasSize(1)
        assertThat(msg.attachments[0]).isEqualTo(attach)
    }

    @Test
    fun `User message defaults to empty attachments`() {
        val msg = AssistantMessage.User(content = "text only")
        assertThat(msg.attachments).isEmpty()
    }
}
