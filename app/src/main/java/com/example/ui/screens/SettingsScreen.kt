package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.CamcorderSettings
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: CamcorderSettings,
    onUpdateSettings: ((CamcorderSettings) -> CamcorderSettings) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_button)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_settings),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingToggleCard(
                    title = "Fluorescent Hum & Static Audio",
                    subtitle = "Real-time synthesized 60Hz tube buzz, VHS hiss, and proximity heartbeat.",
                    checked = settings.audioEnabled,
                    tag = "setting_audio_toggle",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(audioEnabled = enabled) }
                    }
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Master Audio Gain", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${(settings.audioVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.audioVolume,
                            onValueChange = { vol -> onUpdateSettings { it.copy(audioVolume = vol) } },
                            valueRange = 0f..1f,
                            enabled = settings.audioEnabled
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Camera Turn Sensitivity", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = String.format(Locale.US, "%.2fx", settings.lookSensitivity),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.lookSensitivity,
                            onValueChange = { sens -> onUpdateSettings { it.copy(lookSensitivity = sens) } },
                            valueRange = 0.4f..2.2f
                        )
                    }
                }

                SettingToggleCard(
                    title = "Glitch Safety Mode",
                    subtitle = "Caps intense chromatic splits, tracking tears, and haptic jolts while keeping spatial shifts.",
                    checked = settings.glitchSafetyMode,
                    tag = "setting_safety_toggle",
                    onCheckedChange = { safe ->
                        onUpdateSettings { it.copy(glitchSafetyMode = safe) }
                    }
                )

                SettingToggleCard(
                    title = "Invert Horizontal Look",
                    subtitle = "Reverses viewfinder swipe-to-turn direction.",
                    checked = settings.invertLook,
                    tag = "setting_invert_toggle",
                    onCheckedChange = { inv ->
                        onUpdateSettings { it.copy(invertLook = inv) }
                    }
                )

                SettingToggleCard(
                    title = "Spawn Bearing Indicator",
                    subtitle = "Displays a subtle directional marker pointing back toward (0, 0) spawn.",
                    checked = settings.showSignalCompass,
                    tag = "setting_compass_toggle",
                    onCheckedChange = { show ->
                        onUpdateSettings { it.copy(showSignalCompass = show) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(tag)
            )
        }
    }
}
