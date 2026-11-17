package dev.opencode.mobile

enum class ThemeMode(val preference: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Preserve explicit choices made before the three-way theme preference existed. */
        fun fromStored(value: String?, legacyDark: Boolean?): ThemeMode =
            if (value == null) when (legacyDark) {
                true -> DARK
                false -> LIGHT
                null -> SYSTEM
            } else entries.find { it.preference == value } ?: SYSTEM
    }
}
