package com.aure.androidtuner.model

enum class AppColorSource {
    SYSTEM,
    CUSTOM_ACCENT,
}

enum class TileInteractionBehavior {
    SHOW_DIALOG,
    CYCLE_PRESETS,
}

data class AppSettings(
    val colorSource: AppColorSource = AppColorSource.SYSTEM,
    val accentColor: Int = 0xFF3F51B5.toInt(),
    val tileTapBehavior: TileInteractionBehavior = TileInteractionBehavior.SHOW_DIALOG,
    val tileLongPressBehavior: TileInteractionBehavior = TileInteractionBehavior.SHOW_DIALOG,
    val applyLastPresetOnBoot: Boolean = false,
)
