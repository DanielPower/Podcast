package ca.danielpower.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ca.danielpower.podcast.data.CatchUpProgress

/** Marks catching up wherever it shows: on the podcast page and on the queue row. */
val CatchUpIcon: ImageVector get() = Icons.AutoMirrored.Filled.FormatListNumbered

/** Asked on Subscribe: start with the newest episode, or catch up from the first. */
@Composable
fun SubscribeDialog(onSubscribe: (catchUp: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start with") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubscribeChoice(
                    "Latest episode",
                    "Queue the newest, then each new release as it comes out.",
                    onClick = { onSubscribe(false) },
                )
                SubscribeChoice(
                    "From the beginning",
                    "Queue the first episode. When you finish one, the next joins the end of the queue.",
                    onClick = { onSubscribe(true) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.podcastColors.raised,
    )
}

@Composable
private fun SubscribeChoice(title: String, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown on the page of a podcast catching up: how far it has got, and a way to stop. */
@Composable
fun CatchUpBanner(progress: CatchUpProgress, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.primaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CatchUpIcon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Catching up · ${progress.number} of ${progress.total}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
            )
            ThinBar(
                progress.number.toFloat() / progress.total.coerceAtLeast(1),
                colors.primary,
                Modifier.fillMaxWidth(),
                height = 4.dp,
                trackColor = colors.onPrimaryContainer.copy(alpha = 0.18f),
            )
        }
        TextButton(onClick = onStop) { Text("Stop", color = colors.onPrimaryContainer) }
    }
}

/** For a subscribed podcast that isn't catching up: start working through its back catalogue. */
@Composable
fun StartCatchUpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(onClick = onClick, modifier = modifier) {
        Icon(CatchUpIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Catch up from the beginning")
    }
}
