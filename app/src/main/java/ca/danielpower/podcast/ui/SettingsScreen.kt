package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.AppSettings
import ca.danielpower.podcast.data.CardTransition
import ca.danielpower.podcast.data.Scheme
import ca.danielpower.podcast.data.ThemeMode

/** Which list of choices is open in a dialog, if any. */
private enum class Picker { SKIP_BACK, SKIP_FORWARD, KEEP }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    dark: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection("Appearance")
            Text(
                "Colour scheme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = SideMargin, vertical = 6.dp),
            )
            SchemeGrid(selected = settings.scheme, dark = dark) { scheme -> onChange { it.copy(scheme = scheme) } }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SideMargin, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mode in ThemeMode.entries) {
                        PaperChip(
                            selected = settings.themeMode == mode,
                            onClick = { onChange { it.copy(themeMode = mode) } },
                            label = mode.label,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Now playing opens", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (transition in CardTransition.entries) {
                        PaperChip(
                            selected = settings.cardTransition == transition,
                            onClick = { onChange { it.copy(cardTransition = transition) } },
                            label = transition.label,
                        )
                    }
                }
            }

            SettingsSection("Playback")
            ChoiceRow("Skip back", "${settings.skipBackSeconds} s") { picker = Picker.SKIP_BACK }
            ChoiceRow("Skip forward", "${settings.skipForwardSeconds} s") { picker = Picker.SKIP_FORWARD }

            SettingsSection("Downloads")
            SettingRow(
                "Download new episodes",
                hint = "They join the end of the queue",
                onClick = { onChange { it.copy(autoDownload = !it.autoDownload) } },
            ) {
                PaperSwitch(settings.autoDownload, { on -> onChange { it.copy(autoDownload = on) } })
            }
            SettingRow(
                "Only on Wi-Fi",
                hint = "Wait for Wi-Fi before downloading",
                onClick = { onChange { it.copy(wifiOnly = !it.wifiOnly) } },
            ) {
                PaperSwitch(settings.wifiOnly, { on -> onChange { it.copy(wifiOnly = on) } })
            }
            ChoiceRow("Keep removed episodes", keepLabel(settings.keepDays), hint = "Then the download is deleted") {
                picker = Picker.KEEP
            }
        }
    }

    when (picker) {
        Picker.SKIP_BACK -> ChoiceDialog(
            title = "Skip back",
            choices = AppSettings.SKIP_BACK_CHOICES,
            selected = settings.skipBackSeconds,
            label = { "$it seconds" },
            onPick = { s -> onChange { it.copy(skipBackSeconds = s) } },
            onDismiss = { picker = null },
        )
        Picker.SKIP_FORWARD -> ChoiceDialog(
            title = "Skip forward",
            choices = AppSettings.SKIP_FORWARD_CHOICES,
            selected = settings.skipForwardSeconds,
            label = { "$it seconds" },
            onPick = { s -> onChange { it.copy(skipForwardSeconds = s) } },
            onDismiss = { picker = null },
        )
        Picker.KEEP -> ChoiceDialog(
            title = "Keep removed episodes",
            choices = AppSettings.KEEP_DAY_CHOICES,
            selected = settings.keepDays,
            label = ::keepLabel,
            onPick = { d -> onChange { it.copy(keepDays = d) } },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private fun keepLabel(days: Int): String = when (days) {
    1 -> "1 day"
    7 -> "1 week"
    14 -> "2 weeks"
    30 -> "1 month"
    else -> "$days days"
}

@Composable
private fun SettingsSection(title: String) {
    HorizontalDivider(
        Modifier.padding(horizontal = SideMargin).padding(top = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    SectionLabel(title, Modifier.padding(start = SideMargin, end = SideMargin, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ChoiceRow(label: String, value: String, hint: String? = null, onClick: () -> Unit) {
    SettingRow(label, hint = hint, onClick = onClick) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A two-column grid of scheme cards, each showing a small preview in that scheme's colours. */
@Composable
private fun SchemeGrid(selected: Scheme, dark: Boolean, onSelect: (Scheme) -> Unit) {
    Column(
        Modifier.padding(horizontal = SideMargin).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in Scheme.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (scheme in row) {
                    SchemeCard(scheme, scheme == selected, dark, { onSelect(scheme) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SchemeCard(scheme: Scheme, selected: Boolean, dark: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val p = scheme.palette(dark)
    Box(
        modifier
            .clip(PaperShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.outline else colors.outlineVariant, PaperShape)
            .background(MaterialTheme.podcastColors.raised)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column {
            // A tiny screen: the accent square, two lines of text, and a segmented bar.
            Row(
                Modifier.fillMaxWidth().height(64.dp).background(p.bg).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(30.dp).background(p.accent, RoundedCornerShape(3.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth(0.8f).height(5.dp).background(p.ink))
                    Box(Modifier.fillMaxWidth(0.55f).height(4.dp).background(p.soft))
                    Row(Modifier.fillMaxWidth().height(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(3f).height(3.dp).background(p.accent))
                        Box(Modifier.weight(2f).height(3.dp).background(p.track))
                        Box(Modifier.weight(2f).height(3.dp).background(p.track))
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(scheme.label, style = MaterialTheme.typography.titleSmall)
                Text(scheme.blurb, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, minLines = 2)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(colors.onSurface, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (choice in choices) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = choice == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onPick(choice)
                                    onDismiss()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            label(choice),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (choice == selected) FontWeight.Bold else null,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.podcastColors.raised,
    )
}
