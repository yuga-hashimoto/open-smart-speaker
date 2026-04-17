package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class KatakanaCityRomanizerTest {

    @Test
    fun `sydney maps to Sydney`() {
        assertThat(KatakanaCityRomanizer.romanize("シドニー")).isEqualTo("Sydney")
    }

    @Test
    fun `new york maps with space`() {
        assertThat(KatakanaCityRomanizer.romanize("ニューヨーク")).isEqualTo("New York")
    }

    @Test
    fun `los angeles maps with space`() {
        assertThat(KatakanaCityRomanizer.romanize("ロサンゼルス")).isEqualTo("Los Angeles")
    }

    @Test
    fun `london maps to London`() {
        assertThat(KatakanaCityRomanizer.romanize("ロンドン")).isEqualTo("London")
    }

    @Test
    fun `paris maps to Paris`() {
        assertThat(KatakanaCityRomanizer.romanize("パリ")).isEqualTo("Paris")
    }

    @Test
    fun `singapore maps to Singapore`() {
        assertThat(KatakanaCityRomanizer.romanize("シンガポール")).isEqualTo("Singapore")
    }

    @Test
    fun `hong kong is kanji`() {
        assertThat(KatakanaCityRomanizer.romanize("香港")).isEqualTo("Hong Kong")
    }

    @Test
    fun `unknown katakana returns null`() {
        assertThat(KatakanaCityRomanizer.romanize("オフイス")).isNull()
    }

    @Test
    fun `english input returns null`() {
        // English input is pass-through territory for the provider — the
        // romanizer should only return non-null for known mapped entries.
        assertThat(KatakanaCityRomanizer.romanize("Sydney")).isNull()
    }

    @Test
    fun `empty string returns null`() {
        assertThat(KatakanaCityRomanizer.romanize("")).isNull()
    }

    @Test
    fun `kanji japanese place returns null`() {
        // Domestic Japanese places use the suffix-strip path, not the
        // romanizer table.
        assertThat(KatakanaCityRomanizer.romanize("宗像")).isNull()
    }
}
