package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UnitConverterTest {

    private val converter = UnitConverter()

    @Test
    fun `meters to kilometers`() {
        val r = converter.convert(1500.0, "m", "km") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.0001).of(1.5)
    }

    @Test
    fun `miles to kilometers`() {
        val r = converter.convert(10.0, "miles", "km") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.01).of(16.09344)
    }

    @Test
    fun `celsius to fahrenheit`() {
        val r = converter.convert(100.0, "c", "f") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.01).of(212.0)
    }

    @Test
    fun `fahrenheit to celsius`() {
        val r = converter.convert(32.0, "f", "c") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.01).of(0.0)
    }

    @Test
    fun `kilograms to pounds`() {
        val r = converter.convert(1.0, "kg", "lb") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.01).of(2.20462)
    }

    @Test
    fun `cups to milliliters`() {
        val r = converter.convert(1.0, "cup", "ml") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.5).of(236.588)
    }

    @Test
    fun `unknown unit returns UnknownUnit`() {
        val r = converter.convert(1.0, "furlong", "m")
        assertThat(r).isInstanceOf(UnitConverter.Result.UnknownUnit::class.java)
    }

    @Test
    fun `incompatible dimensions returns error`() {
        val r = converter.convert(1.0, "kg", "m")
        assertThat(r).isInstanceOf(UnitConverter.Result.IncompatibleDimensions::class.java)
    }

    @Test
    fun `case insensitive and plural tolerant`() {
        val r = converter.convert(1.0, "Meters", "Kilometers") as UnitConverter.Result.Converted
        assertThat(r.value).isWithin(0.0001).of(0.001)
    }
}
