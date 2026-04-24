package com.aure.androidtuner.model

enum class TileInteractionBehavior {
    SHOW_DIALOG,
    CYCLE_PRESETS,
}

data class AppSettings(
    val tileTapBehavior: TileInteractionBehavior = TileInteractionBehavior.SHOW_DIALOG,
    val tileLongPressBehavior: TileInteractionBehavior = TileInteractionBehavior.SHOW_DIALOG,
    val applyLastPresetOnBoot: Boolean = false,
)
