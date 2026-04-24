package com.aure.androidtuner.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.aure.androidtuner.AppContainer
import com.aure.androidtuner.R
import com.aure.androidtuner.TileControlActivity
import com.aure.androidtuner.model.TileInteractionBehavior
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PerformanceTileService : TileService() {

    companion object {
        private const val TAG = "PerformanceTile"
    }

    override fun onStartListening() {
        super.onStartListening()
        runCatching {
            val container = AppContainer(applicationContext)
            val state = runBlocking { container.repository.observeState().first() }
            qsTile?.apply {
                label = getString(R.string.tile_title)
                subtitle = when {
                    !state.isPServerAvailable -> getString(R.string.tile_state_unavailable)
                    else -> state.activeDisplayProfileName ?: getString(R.string.tile_state_manual)
                }
                this.state = when {
                    !state.isPServerAvailable -> Tile.STATE_UNAVAILABLE
                    state.activeDisplayProfileId == com.aure.androidtuner.model.PresetStateResolver.STOCK_PROFILE_ID -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                updateTile()
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to refresh tile state", throwable)
            qsTile?.apply {
                label = getString(R.string.tile_title)
                subtitle = getString(R.string.tile_state_unavailable)
                state = Tile.STATE_UNAVAILABLE
                updateTile()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun(::handleTap)
        } else {
            handleTap()
        }
    }

    private fun handleTap() {
        runCatching {
            val container = AppContainer(applicationContext)
            val settings = runBlocking { container.settingsStorage.settings.first() }
            when (settings.tileTapBehavior) {
                TileInteractionBehavior.SHOW_DIALOG -> {
                    startActivityAndCollapse(TileControlActivity.createDialogIntent(applicationContext))
                }
                TileInteractionBehavior.CYCLE_PRESETS -> {
                    runBlocking {
                        container.repository.cycleTilePreset()
                            .onSuccess { profile ->
                                Toast.makeText(
                                    applicationContext,
                                    "Applied ${profile.name}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                    onStartListening()
                }
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to handle tile tap", throwable)
        }
    }
}
