package com.aure.androidtuner.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aure.androidtuner.model.CpuPolicyInfo
import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.PresetStateResolver
import com.aure.androidtuner.model.ProfileSource
import com.aure.androidtuner.model.TunerState
import kotlinx.coroutines.delay

private const val NEW_PRESET_DIALOG_ID = "__new_preset__"

@Composable
fun MainTunerScreen(
    state: TunerState,
    onPolicyValueChange: (CpuPolicyInfo, Int) -> Unit,
    onApplyProfile: (PerformanceProfile) -> Unit,
    onClearSelection: () -> Unit,
    onApplyCurrent: (TunerState) -> Unit,
    onCreatePreset: (String, TunerState) -> Unit,
    onUpdatePreset: (String, String, TunerState) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit,
    onResetProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshLiveValues: () -> Unit,
    onRefreshStructure: () -> Unit,
) {
    var dialogProfileId by remember { mutableStateOf<String?>(null) }

    ScreenNotifications(state)

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            onRefreshLiveValues()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            onRefreshStructure()
        }
    }

    ScreenContainer(compactMode = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(
                state = state,
                compactMode = false,
                onOpenSettings = onOpenSettings,
            )

            if (!state.isPServerAvailable) {
                Text(
                    text = "Your device is not compatible with this app",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                CurrentFrequenciesCard(
                    state = state,
                    onEditManual = { dialogProfileId = PresetStateResolver.MANUAL_PROFILE_ID },
                )

                when {
                    state.isReadingStockValues -> {
                        StockReadStatusCard()
                    }

                    state.stockReadError != null -> {
                        StockReadErrorCard(state.stockReadError)
                    }

                    else -> {
                        PresetListSection(
                            state = state,
                            onApplyProfile = onApplyProfile,
                            onOpenCreatePreset = { dialogProfileId = NEW_PRESET_DIALOG_ID },
                            onEditPreset = { dialogProfileId = it },
                            onMovePreset = onMovePreset,
                            onApplySelectedPreset = { onApplyCurrent(state) },
                        )
                    }
                }
            }
        }
    }

    dialogProfileId?.let { profileId ->
        val manualProfile = remember(state.actualValues, state.policies) {
            if (state.policies.isEmpty()) {
                null
            } else {
                PerformanceProfile(
                    id = PresetStateResolver.MANUAL_PROFILE_ID,
                    name = "Manual",
                    maxFrequencies = state.policies.associate { policy ->
                        policy.id to (state.actualValues[policy.id] ?: policy.currentMaxFreq)
                    },
                    source = ProfileSource.VIRTUAL,
                    isEditable = true,
                    isDeletable = false,
                )
            }
        }
        val profile = when (profileId) {
            PresetStateResolver.MANUAL_PROFILE_ID -> manualProfile
            else -> state.displayProfiles.firstOrNull { it.id == profileId }
        }
        PresetEditorDialog(
            baseState = state,
            profile = profile,
            creatingNewPreset = profileId == NEW_PRESET_DIALOG_ID,
            manualMode = profileId == PresetStateResolver.MANUAL_PROFILE_ID,
            onDismiss = { dialogProfileId = null },
            onSave = { name, values ->
                val editedState = state.copy(currentValues = values)
                when {
                    profileId == NEW_PRESET_DIALOG_ID -> onCreatePreset(name, editedState)
                    profileId == PresetStateResolver.MANUAL_PROFILE_ID -> onApplyCurrent(editedState)
                    profile != null -> onUpdatePreset(profile.id, name, editedState)
                }
                dialogProfileId = null
            },
            onDelete = {
                profile?.let { onDeletePreset(it.id) }
                dialogProfileId = null
            },
        )
    }
}

@Composable
fun CompactTunerScreen(
    state: TunerState,
    onPolicyValueChange: (CpuPolicyInfo, Int) -> Unit,
    onApplyProfile: (PerformanceProfile) -> Unit,
    onClearSelection: () -> Unit,
    onApplyCurrent: (TunerState) -> Unit,
    onCreatePreset: (String, TunerState) -> Unit,
    onUpdatePreset: (String, String, TunerState) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit,
    onResetProfiles: () -> Unit,
    onDismissRequest: (() -> Unit)?,
    onOpenFullApp: (() -> Unit)? = null,
) {
    ScreenNotifications(state)

    ScreenContainer(compactMode = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                state = state,
                compactMode = true,
                onOpenSettings = null,
            )
            when {
                state.isReadingStockValues -> {
                    StockReadStatusCard()
                }

                state.stockReadError != null -> {
                    StockReadErrorCard(state.stockReadError)
                }

                else -> {
                    PresetChipSelector(
                        state = state,
                        onApplyProfile = onApplyProfile,
                        onClearSelection = onClearSelection,
                        onOpenFullApp = onOpenFullApp,
                    )
                    PolicyEditorSection(
                        state = state,
                        onPolicyValueChange = onPolicyValueChange,
                        compactMode = true,
                    )
                }
            }

            if (onDismissRequest != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onApplyCurrent(state)
                            onDismissRequest()
                        },
                        enabled = state.policies.isNotEmpty() && state.isPServerAvailable,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun StockReadStatusCard() {
    SectionCard(
        title = null,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
            )
            Text(
                text = "Reading stock values",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StockReadErrorCard(message: String) {
    SectionCard(
        title = null,
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ScreenNotifications(state: TunerState) {
    val context = LocalContext.current

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun ScreenContainer(
    compactMode: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundModifier = if (compactMode) {
        Modifier.fillMaxSize().background(colorScheme.scrim.copy(alpha = 0.45f))
    } else {
        Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colorScheme.primaryContainer.copy(alpha = 0.9f),
                    colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    colorScheme.surface,
                ),
            ),
        )
    }

    Box(modifier = backgroundModifier) {
        val containerModifier = if (compactMode) {
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        } else {
            Modifier.fillMaxSize()
        }

        Card(
            modifier = containerModifier,
            shape = if (compactMode) RoundedCornerShape(30.dp, 30.dp, 24.dp, 24.dp) else RectangleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (compactMode) colorScheme.surfaceColorAtElevation(4.dp) else Color.Transparent,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun Header(
    state: TunerState,
    compactMode: Boolean,
    onOpenSettings: (() -> Unit)?,
) {
    if (compactMode && state.statusMessage == null && state.errorMessage == null) return

    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 8.dp)) {
        if (!compactMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Handheld Performance",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                onOpenSettings?.let { openSettings ->
                    IconButton(onClick = openSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        state.statusMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CurrentFrequenciesCard(
    state: TunerState,
    onEditManual: () -> Unit = {},
) {
    SectionCard(
        title = null,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        if (state.policies.isEmpty()) {
            Text("No CPU policies found.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Current values",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ValuePreviewChips(
                    values = state.policies.associate { policy ->
                        policy.id to (state.actualValues[policy.id] ?: policy.currentMaxFreq)
                    },
                    modifier = Modifier.weight(1f),
                )
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    IconButton(
                        onClick = onEditManual,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit manual settings",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetListSection(
    state: TunerState,
    onApplyProfile: (PerformanceProfile) -> Unit,
    onOpenCreatePreset: () -> Unit,
    onEditPreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit,
    onApplySelectedPreset: () -> Unit,
) {
    SectionCard(
        title = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onOpenCreatePreset) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "New",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.displayProfiles.forEach { profile ->
                val movableIndex = state.displayProfiles.indexOfFirst { it.id == profile.id }
                val canMove = movableIndex >= 0
                PresetListRow(
                    profile = profile,
                    isApplied = profile.id == state.activeDisplayProfileId,
                    isSelected = profile.id == state.selectedDisplayProfileId,
                    canMoveUp = canMove && movableIndex > 0,
                    canMoveDown = canMove && movableIndex < state.displayProfiles.lastIndex,
                    showReorder = true,
                    showEdit = profile.isEditable,
                    valuePreview = profile.maxFrequencies,
                    onClick = { onApplyProfile(profile) },
                    onEdit = {
                        if (profile.isEditable) {
                            onEditPreset(profile.id)
                        }
                    },
                    onMovePreset = { offset -> onMovePreset(profile.id, offset) },
                )
            }
        }

        val canApplySelectedPreset = state.selectedDisplayProfileId != null &&
            state.policies.isNotEmpty() &&
            state.isPServerAvailable
        Spacer(Modifier.size(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onApplySelectedPreset,
                enabled = canApplySelectedPreset,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = state.selectedDisplayProfileName?.let { "Apply $it" }
                        ?: "Select a preset to apply",
                )
            }
        }
    }
}

@Composable
private fun PresetListRow(
    profile: PerformanceProfile,
    isApplied: Boolean,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showReorder: Boolean,
    showEdit: Boolean,
    valuePreview: Map<Int, Int>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMovePreset: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(20.dp)
    val containerColor = when {
        isApplied && isSelected -> colorScheme.primaryContainer
        isApplied -> colorScheme.primaryContainer
        else -> colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isApplied && isSelected -> colorScheme.onPrimaryContainer
        isApplied -> colorScheme.onPrimaryContainer
        else -> colorScheme.onSurface
    }
    val borderColor = when {
        isApplied -> colorScheme.primary
        isSelected -> colorScheme.primary
        else -> Color.Transparent
    }
    val chipContainerColor = when {
        isApplied -> colorScheme.secondaryContainer.copy(alpha = 0.92f)
        else -> colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val chipContentColor = when {
        isApplied -> colorScheme.onSecondaryContainer
        else -> colorScheme.onPrimaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, rowShape)
            .border(BorderStroke(2.dp, borderColor), rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showReorder) {
            ReorderControl(
                enabled = true,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMovePreset = onMovePreset,
            )
        } else {
            Spacer(Modifier.width(64.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            if (valuePreview.isNotEmpty()) {
                ValuePreviewChips(
                    values = valuePreview,
                    chipContainerColor = chipContainerColor,
                    chipContentColor = chipContentColor,
                )
            }
        }
        if (showEdit) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit ${profile.name}",
                    tint = contentColor,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun ReorderControl(
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMovePreset: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = { onMovePreset(-1) },
            enabled = enabled && canMoveUp,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.ExpandLess,
                contentDescription = "Move up",
                tint = if (canMoveUp) colorScheme.primary else colorScheme.outline,
            )
        }
        IconButton(
            onClick = { onMovePreset(1) },
            enabled = enabled && canMoveDown,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = "Move down",
                tint = if (canMoveDown) colorScheme.primary else colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ValuePreviewChips(
    values: Map<Int, Int>,
    modifier: Modifier = Modifier,
    chipContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
    chipContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.toSortedMap().forEach { (policyId, value) ->
            Surface(
                color = chipContainerColor,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "P$policyId: ${formatFrequency(value)}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = chipContentColor,
                )
            }
        }
    }
}

@Composable
private fun PresetChipSelector(
    state: TunerState,
    onApplyProfile: (PerformanceProfile) -> Unit,
    onClearSelection: () -> Unit,
    onOpenFullApp: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.displayProfiles.forEach { profile ->
                PresetSelectorChip(
                    label = profile.name,
                    isApplied = profile.id == state.activeDisplayProfileId,
                    isSelected = profile.id == state.selectedDisplayProfileId,
                    onClick = { onApplyProfile(profile) },
                )
            }
            if (state.isManualSelection) {
                PresetSelectorChip(
                    label = "Manual",
                    isApplied = false,
                    isSelected = true,
                    onClick = onClearSelection,
                )
            }
        }
        onOpenFullApp?.let { openFullApp ->
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            ) {
                IconButton(
                    onClick = openFullApp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Open full app",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSelectorChip(
    label: String,
    isApplied: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        colors = when {
            isApplied -> AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> AssistChipDefaults.assistChipColors()
        },
        border = when {
            isApplied -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        },
        label = { Text(label) },
    )
}

@Composable
private fun PolicyEditorSection(
    state: TunerState,
    onPolicyValueChange: (CpuPolicyInfo, Int) -> Unit,
    compactMode: Boolean,
) {
    if (state.policies.isEmpty()) {
        EmptyState(state)
        return
    }

    state.policies.forEach { policy ->
        PolicyCard(
            policy = policy,
            selectedValue = state.currentValues[policy.id] ?: policy.currentMaxFreq,
            actualValue = state.actualValues[policy.id] ?: policy.currentMaxFreq,
            onValueChanged = { onPolicyValueChange(policy, it) },
            compactMode = compactMode,
        )
    }
}

@Composable
private fun PresetEditorDialog(
    baseState: TunerState,
    profile: PerformanceProfile?,
    creatingNewPreset: Boolean,
    manualMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<Int, Int>) -> Unit,
    onDelete: () -> Unit,
) {
    val initialValues = remember(profile?.id, creatingNewPreset, manualMode, baseState.actualValues) {
        baseState.policies.associate { policy ->
            val initialValue = when {
                creatingNewPreset || manualMode -> baseState.actualValues[policy.id]
                else -> profile?.maxFrequencies?.get(policy.id)
            } ?: policy.currentMaxFreq
            policy.id to initialValue
        }
    }
    var presetName by remember(profile?.id, creatingNewPreset) { mutableStateOf(profile?.name.orEmpty()) }
    var editedValues by remember(profile?.id, initialValues) { mutableStateOf(initialValues) }
    var showDeleteConfirmation by remember(profile?.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .widthIn(max = 900.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!manualMode) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Preset name") },
                    )
                }
                baseState.policies.forEach { policy ->
                    PolicyCard(
                        policy = policy,
                        selectedValue = editedValues[policy.id] ?: policy.currentMaxFreq,
                        actualValue = baseState.actualValues[policy.id] ?: policy.currentMaxFreq,
                        onValueChanged = { editedValue ->
                            editedValues = editedValues + (policy.id to editedValue)
                        },
                        compactMode = true,
                    )
                }
                if (manualMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSave(profile?.name.orEmpty(), editedValues) },
                            enabled = baseState.policies.isNotEmpty(),
                        ) {
                            Text("Apply custom values")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (profile?.isDeletable == true) {
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete preset",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { onSave(presetName, editedValues) },
                                enabled = presetName.isNotBlank() && baseState.policies.isNotEmpty(),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete preset?") },
            text = { Text("This preset will be removed until you reset presets to default.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EmptyState(state: TunerState) {
    SectionCard(title = "No CPU Policies Found") {
        Text(
            text = if (state.isLoading) {
                "Scanning cpufreq policies..."
            } else {
                "No compatible cpufreq policy directories were found under /sys/devices/system/cpu/cpufreq."
            },
        )
    }
}

@Composable
private fun PolicyCard(
    policy: CpuPolicyInfo,
    selectedValue: Int,
    onValueChanged: (Int) -> Unit,
    compactMode: Boolean = false,
    actualValue: Int = selectedValue,
) {
    val supported = policy.supportedFrequencies
    val currentIndex = supported.indexOf(selectedValue).takeIf { it >= 0 } ?: 0

    SectionCard(title = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Policy ${policy.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Sel ${formatFrequency(selectedValue)}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
            Text(
                text = "Now ${formatFrequency(actualValue)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (actualValue == selectedValue) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                textAlign = TextAlign.End,
            )
        }
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides if (compactMode) Dp.Unspecified else 48.dp,
        ) {
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { raw ->
                    val index = raw.toInt().coerceIn(0, supported.lastIndex)
                    onValueChanged(supported[index])
                },
                valueRange = 0f..supported.lastIndex.toFloat(),
                steps = (supported.size - 2).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String?,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

internal fun formatFrequency(valueKhz: Int): String {
    return when {
        valueKhz >= 1_000_000 -> String.format("%.2f GHz", valueKhz / 1_000_000f)
        valueKhz >= 1_000 -> String.format("%.0f MHz", valueKhz / 1_000f)
        else -> "$valueKhz kHz"
    }
}
