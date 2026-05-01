package com.aure.androidtuner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aure.androidtuner.model.TileInteractionBehavior
import com.aure.androidtuner.ui.CompactTunerScreen
import com.aure.androidtuner.ui.TunerViewModel
import com.aure.androidtuner.ui.theme.AndroidTunerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TileControlActivity : ComponentActivity() {

    companion object {
        private const val ACTION_OPEN_DIALOG = "com.aure.androidtuner.action.OPEN_TILE_DIALOG"
        private const val ACTION_QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"

        fun createDialogIntent(context: Context): Intent {
            return Intent(context, TileControlActivity::class.java).apply {
                action = ACTION_OPEN_DIALOG
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private val container by lazy { AppContainer(this) }
    private val viewModel by viewModels<TunerViewModel> {
        TunerViewModel.factory(
            repository = container.repository,
            settingsStorage = container.settingsStorage,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val launchedFromLongPress = intent?.action == ACTION_QS_TILE_PREFERENCES

            if (launchedFromLongPress) {
                val settings = container.settingsStorage.settings.first()
                if (settings.tileLongPressBehavior == TileInteractionBehavior.CYCLE_PRESETS) {
                    container.repository.cycleTilePreset()
                        .onSuccess { profile ->
                            Toast.makeText(
                                applicationContext,
                                "Applied ${profile.name}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        .onFailure { throwable ->
                            Toast.makeText(
                                applicationContext,
                                throwable.message ?: "Failed to cycle preset",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    finish()
                    return@launch
                }
            }

            setEditorContent()
        }
    }

    private fun setEditorContent() {
        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value
            AndroidTunerTheme(settings = settings) {
                Surface {
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    CompactTunerScreen(
                        state = state,
                        onPolicyValueChange = viewModel::setPolicyValue,
                        onApplyProfile = viewModel::applyProfile,
                        onClearSelection = viewModel::clearSelection,
                        onApplyCurrent = { tunerState ->
                            viewModel.applyCurrent(tunerState) { presetName ->
                                Toast.makeText(applicationContext, "Applied $presetName", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCreatePreset = viewModel::createUserPreset,
                        onUpdatePreset = viewModel::updateProfile,
                        onDeletePreset = viewModel::deletePreset,
                        onMovePreset = viewModel::moveProfile,
                        onResetProfiles = viewModel::resetProfilesToDefault,
                        onDismissRequest = ::finish,
                        onOpenFullApp = {
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                },
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }
}
