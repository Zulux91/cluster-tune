package com.aure.androidtuner

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aure.androidtuner.ui.MainTunerScreen
import com.aure.androidtuner.ui.SettingsScreen
import com.aure.androidtuner.ui.TunerViewModel
import com.aure.androidtuner.ui.theme.AndroidTunerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }
    private val viewModel by viewModels<TunerViewModel> {
        TunerViewModel.factory(
            repository = container.repository,
            settingsStorage = container.settingsStorage,
        )
    }
    private val exportPresetsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(::exportPresetsToUri)
    }
    private val importPresetsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importPresetsFromUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value
            AndroidTunerTheme(settings = settings) {
                Surface {
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    var showSettings by rememberSaveable { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            onBack = { showSettings = false },
                            onColorSourceChange = viewModel::setColorSource,
                            onAccentColorChange = viewModel::setAccentColor,
                            onTileTapBehaviorChange = viewModel::setTileTapBehavior,
                            onTileLongPressBehaviorChange = viewModel::setTileLongPressBehavior,
                            onApplyLastPresetOnBootChange = viewModel::setApplyLastPresetOnBoot,
                            onResetProfiles = viewModel::resetProfilesToDefault,
                            onExportPresets = {
                                exportPresetsLauncher.launch("android-tuner-presets.json")
                            },
                            onImportPresets = {
                                importPresetsLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                        )
                    } else {
                        MainTunerScreen(
                            state = state,
                            onPolicyValueChange = viewModel::setPolicyValue,
                            onApplyProfile = viewModel::applyProfile,
                            onClearSelection = viewModel::clearSelection,
                            onApplyCurrent = { tunerState -> viewModel.applyCurrent(tunerState) },
                            onCreatePreset = viewModel::createUserPreset,
                            onUpdatePreset = viewModel::updateProfile,
                            onDeletePreset = viewModel::deletePreset,
                            onMovePreset = viewModel::moveProfile,
                            onResetProfiles = viewModel::resetProfilesToDefault,
                            onOpenSettings = { showSettings = true },
                            onRefreshLiveValues = viewModel::refreshLiveState,
                            onRefreshStructure = viewModel::refreshStructureState,
                        )
                    }
                }
            }
        }
    }

    private fun exportPresetsToUri(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val json = viewModel.exportProfilesJson()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                } ?: error("Unable to open export file")
            }.onSuccess {
                Toast.makeText(applicationContext, "Exported presets", Toast.LENGTH_SHORT).show()
            }.onFailure { throwable ->
                Toast.makeText(
                    applicationContext,
                    throwable.message ?: "Failed to export presets",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun importPresetsFromUri(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open import file")
                viewModel.importProfilesJson(json)
            }.onSuccess { importedCount ->
                Toast.makeText(
                    applicationContext,
                    "Imported $importedCount presets",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { throwable ->
                Toast.makeText(
                    applicationContext,
                    throwable.message ?: "Failed to import presets",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
