package com.example.todowallapp.data.model

import androidx.compose.ui.graphics.Color

enum class WeatherCondition(
    val lightTintColor: Color,
    val darkTintColor: Color,
    val icon: String
) {
    CLEAR(Color(0x14FFD54F), Color(0x38FFD54F), "☀"),           // Warm gold wash
    PARTLY_CLOUDY(Color.Transparent, Color.Transparent, "⛅"),   // No tint (neutral default)
    CLOUDY(Color(0x0A90A4AE), Color(0x2090A4AE), "☁"),          // Cool grey
    RAIN(Color(0x12607D8B), Color(0x30607D8B), "🌧"),            // Slate blue
    SNOW(Color(0x0ECFD8DC), Color(0x28CFD8DC), "❄"),             // Soft white/lavender
    STORM(Color(0x12FFAB91), Color(0x30FFAB91), "⚡");           // Muted amber

    /** Theme-aware tint: boosted alpha for dark surfaces where subtle washes disappear. */
    fun tintColor(isDark: Boolean): Color = if (isDark) darkTintColor else lightTintColor

    @Deprecated("Use tintColor(isDark) for theme-aware tinting", ReplaceWith("tintColor(isDark)"))
    val tintColor: Color get() = lightTintColor

    companion object {
        /**
         * Map OpenWeatherMap condition codes to our simplified enum.
         * See: https://openweathermap.org/weather-conditions
         */
        fun fromOwmCode(code: Int): WeatherCondition = when (code) {
            in 200..299 -> STORM        // Thunderstorm
            in 300..399 -> RAIN         // Drizzle
            in 500..599 -> RAIN         // Rain
            in 600..699 -> SNOW         // Snow
            in 700..762 -> CLOUDY       // Atmosphere (mist, fog, haze)
            771, 781 -> STORM           // Squall, tornado
            800 -> CLEAR                // Clear sky
            801 -> PARTLY_CLOUDY        // Few clouds
            in 802..804 -> CLOUDY       // Scattered/broken/overcast clouds
            else -> PARTLY_CLOUDY
        }
    }
}
