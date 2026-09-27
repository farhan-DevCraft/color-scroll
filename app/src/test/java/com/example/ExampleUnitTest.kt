package com.example

import com.example.model.ColorPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testYellowPresetMatching() {
        val yellow = ColorPreset.YELLOW
        // Pure yellow hue (~60 deg), high saturation and value
        assertTrue(yellow.matches(hue = 55f, saturation = 0.85f, value = 0.90f))
        // Outside yellow hue
        assertFalse(yellow.matches(hue = 120f, saturation = 0.85f, value = 0.90f))
        // Too desaturated (e.g. gray or white)
        assertFalse(yellow.matches(hue = 55f, saturation = 0.10f, value = 0.90f))
        // Too dark
        assertFalse(yellow.matches(hue = 55f, saturation = 0.85f, value = 0.10f))
    }

    @Test
    fun testGreenPresetMatching() {
        val green = ColorPreset.GREEN
        // Pure green hue (~120 deg)
        assertTrue(green.matches(hue = 110f, saturation = 0.70f, value = 0.80f))
        // Outside green hue
        assertFalse(green.matches(hue = 220f, saturation = 0.70f, value = 0.80f))
    }

    @Test
    fun testRedPresetHueWrapping() {
        val red = ColorPreset.RED
        // 355 deg (before 360)
        assertTrue(red.matches(hue = 355f, saturation = 0.80f, value = 0.80f))
        // 5 deg (after 0)
        assertTrue(red.matches(hue = 5f, saturation = 0.80f, value = 0.80f))
        // 180 deg (Cyan - should not match)
        assertFalse(red.matches(hue = 180f, saturation = 0.80f, value = 0.80f))
    }

    @Test
    fun testPresetFromId() {
        assertEquals(ColorPreset.YELLOW, ColorPreset.fromId("yellow", ColorPreset.GREEN))
        assertEquals(ColorPreset.GREEN, ColorPreset.fromId("green", ColorPreset.YELLOW))
        assertEquals(ColorPreset.BLUE, ColorPreset.fromId("invalid_id", ColorPreset.BLUE))
    }
}

