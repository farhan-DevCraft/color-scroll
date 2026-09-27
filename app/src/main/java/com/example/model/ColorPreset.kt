package com.example.model

enum class ColorPreset(
    val id: String,
    val displayName: String,
    val colorHex: Long,
    val minHue: Float,
    val maxHue: Float,
    val minSaturation: Float = 0.35f,
    val minValue: Float = 0.25f
) {
    YELLOW(
        id = "yellow",
        displayName = "Yellow",
        colorHex = 0xFFFFEB3B,
        minHue = 42f,
        maxHue = 68f,
        minSaturation = 0.35f,
        minValue = 0.30f
    ),
    GREEN(
        id = "green",
        displayName = "Green",
        colorHex = 0xFF4CAF50,
        minHue = 75f,
        maxHue = 160f,
        minSaturation = 0.32f,
        minValue = 0.25f
    ),
    RED(
        id = "red",
        displayName = "Red",
        colorHex = 0xFFF44336,
        minHue = 345f,
        maxHue = 15f,
        minSaturation = 0.45f,
        minValue = 0.30f
    ),
    BLUE(
        id = "blue",
        displayName = "Blue",
        colorHex = 0xFF2196F3,
        minHue = 195f,
        maxHue = 250f,
        minSaturation = 0.40f,
        minValue = 0.25f
    ),
    ORANGE(
        id = "orange",
        displayName = "Orange",
        colorHex = 0xFFFF9800,
        minHue = 18f,
        maxHue = 42f,
        minSaturation = 0.45f,
        minValue = 0.35f
    ),
    CYAN(
        id = "cyan",
        displayName = "Cyan",
        colorHex = 0xFF00BCD4,
        minHue = 165f,
        maxHue = 195f,
        minSaturation = 0.35f,
        minValue = 0.30f
    ),
    PURPLE(
        id = "purple",
        displayName = "Purple",
        colorHex = 0xFF9C27B0,
        minHue = 265f,
        maxHue = 315f,
        minSaturation = 0.35f,
        minValue = 0.25f
    );

    fun matches(hue: Float, saturation: Float, value: Float): Boolean {
        if (saturation < minSaturation || value < minValue) return false
        return if (minHue <= maxHue) {
            hue in minHue..maxHue
        } else {
            hue >= minHue || hue <= maxHue
        }
    }

    companion object {
        fun fromId(id: String, default: ColorPreset): ColorPreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}
