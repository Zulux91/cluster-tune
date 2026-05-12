package com.aure.clustertune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aure.clustertune.data.DeviceProfileGenerator
import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.PerformanceProfile
import kotlin.math.roundToInt

private data class OnboardingEntry(
    val percentage: Int,
    val name: String,
    val frequencies: Map<Int, Int>,
)

private fun bundledProfilePercent(profile: PerformanceProfile, policies: List<CpuPolicyInfo>): Int? {
    val ratios = policies.mapNotNull { policy ->
        profile.maxFrequencies[policy.id]?.toDouble()?.div(policy.selectableMaxFreq)
    }
    if (ratios.isEmpty()) return null
    val percent = (ratios.average() * 100).roundToInt()
    return if (percent >= 100) null else percent
}

private val defaultTiers = listOf(
    Triple(85, "Light Underclock",   "light"),
    Triple(75, "Medium Underclock",  "medium"),
    Triple(65, "Heavy Underclock",   "heavy"),
    Triple(50, "Extreme Underclock", "extreme"),
    Triple(40, "Ultra Underclock",   "ultra"),
)

@Composable
fun OnboardingScreen(
    policies: List<CpuPolicyInfo>,
    bundledProfiles: List<PerformanceProfile> = emptyList(),
    onComplete: (List<Pair<String, Map<Int, Int>>>, List<String>) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    val allEntries = remember(policies) {
        if (policies.isEmpty()) emptyList()
        else defaultTiers.map { (pct, name, _) ->
            OnboardingEntry(
                percentage = pct,
                name = name,
                frequencies = DeviceProfileGenerator.frequenciesForPercentage(policies, pct),
            )
        }
    }

    var selectedPercentages by remember(allEntries) {
        mutableStateOf(allEntries.filter { it.percentage >= 65 }.map { it.percentage }.toSet())
    }

    var useBundled by remember { mutableStateOf(bundledProfiles.isNotEmpty()) }

    var selectedBundledIds by remember(bundledProfiles) {
        mutableStateOf(bundledProfiles.map { it.id }.toSet())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primaryContainer.copy(alpha = 0.9f),
                        colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        colorScheme.surface,
                    ),
                ),
            ),
    ) {
        if (policies.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Set Up Profiles",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (bundledProfiles.isEmpty())
                            "Choose which performance presets to create for your device. You can add more and edit these anytime from the app."
                        else
                            "Your device has optimized profiles available. Pick a setup method below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                if (bundledProfiles.isNotEmpty()) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = useBundled,
                            onClick = { useBundled = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Device Profiles") },
                        )
                        SegmentedButton(
                            selected = !useBundled,
                            onClick = { useBundled = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Auto") },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (useBundled && bundledProfiles.isNotEmpty()) {
                        bundledProfiles.forEach { profile ->
                            val isSelected = profile.id in selectedBundledIds
                            BundledProfileCard(
                                profile = profile,
                                policies = policies,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedBundledIds = if (isSelected)
                                        selectedBundledIds - profile.id
                                    else
                                        selectedBundledIds + profile.id
                                },
                            )
                        }
                    } else {
                        allEntries.forEach { entry ->
                            val isSelected = entry.percentage in selectedPercentages
                            EntryCard(
                                entry = entry,
                                policies = policies,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedPercentages = if (isSelected) {
                                        selectedPercentages - entry.percentage
                                    } else {
                                        selectedPercentages + entry.percentage
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (useBundled && bundledProfiles.isNotEmpty()) {
                            onComplete(
                                bundledProfiles
                                    .filter { it.id in selectedBundledIds }
                                    .map { profile -> profile.name to profile.maxFrequencies },
                                bundledProfiles.map { it.id },
                            )
                        } else {
                            onComplete(
                                allEntries
                                    .filter { it.percentage in selectedPercentages }
                                    .map { it.name to it.frequencies },
                                bundledProfiles.map { it.id },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = if (useBundled && bundledProfiles.isNotEmpty()) selectedBundledIds.isNotEmpty()
                              else selectedPercentages.isNotEmpty(),
                ) {
                    Text("Get Started")
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: OnboardingEntry,
    policies: List<CpuPolicyInfo>,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                    PercentageBadge(
                        percentage = entry.percentage,
                        isSelected = isSelected,
                    )
                }

                policies.forEach { policy ->
                    val freq = entry.frequencies[policy.id]
                    if (freq != null) {
                        val label = if (policies.size > 1) {
                            "Cluster ${policy.id} (${policy.cpuIds.size} cores): ${formatFrequency(freq)}"
                        } else {
                            formatFrequency(freq)
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun PercentageBadge(percentage: Int, isSelected: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isSelected) {
            colorScheme.primary.copy(alpha = 0.15f)
        } else {
            colorScheme.primaryContainer
        },
    ) {
        Text(
            text = "$percentage%",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) colorScheme.primary else colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BundledProfileCard(
    profile: PerformanceProfile,
    policies: List<CpuPolicyInfo>,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val percent = bundledProfilePercent(profile, policies)

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                    if (percent != null) {
                        PercentageBadge(percentage = percent, isSelected = isSelected)
                    }
                }
                policies.forEach { policy ->
                    val freq = profile.maxFrequencies[policy.id]
                    if (freq != null) {
                        val label = if (policies.size > 1)
                            "Cluster ${policy.id} (${policy.cpuIds.size} cores): ${formatFrequency(freq)}"
                        else formatFrequency(freq)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp),
            )
        }
    }
}
