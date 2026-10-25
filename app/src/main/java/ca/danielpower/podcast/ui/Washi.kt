package ca.danielpower.podcast.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Shared pieces of the Washi look (after Jiwa Jiwa's ui/Washi.kt). Use these on new screens rather
// than stock Material components.

// ------------------------------------------------------------------ headers and labels

/** A screen's title row: the accent square with the screen's icon, the title, then [actions]. */
@Composable
fun ScreenHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        // At least an icon button's height, so tabs with and without actions line up.
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, PaperShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontSize = 24.sp, modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * The title row of a page opened on top of a tab (a podcast, History, Settings): a back
 * arrow, then [content] (usually [PageTitle]), then [actions]. The same height as [ScreenHeader].
 */
@Composable
fun PageHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(PageHeaderHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { content() }
        actions()
    }
}

/** A page's title, for [PageHeader]. */
@Composable
fun PageTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontSize = 24.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** [ScreenHeader]'s height: 16 dp above and 8 dp below a 48 dp row. */
private val PageHeaderHeight = 72.dp

/** A small spaced-out heading over a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ buttons

/** The main action on a screen: solid ink. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PaperShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
        content = content,
    )
}

/** A secondary action: an ink outline. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PaperShape,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * A choice: ink when selected, an ink outline when not. When not [enabled] it's greyed out but still
 * takes taps, so the screen can explain why it's unavailable.
 */
@Composable
fun PaperChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(34.dp)
            .clip(PaperShape)
            .border(1.dp, if (enabled) colors.outline else colors.outlineVariant, PaperShape)
            .background(if (selected) colors.onSurface else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> colors.surface
                enabled -> colors.onSurface
                else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
    }
}

/** An on/off switch with square corners: accent when on, the track colour when off. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = if (checked) colors.primary else colors.onSurfaceVariant
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(PaperShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(colors.surface, RoundedCornerShape(3.dp)))
    }
}

// ------------------------------------------------------------------ bits

/** Podcast artwork with small corners, on the track colour until it loads. */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
            .size(size)
            .clip(PaperShape)
            .background(MaterialTheme.podcastColors.track),
    )
}

/** A thin bar, filled to [fraction] (0 to 1). */
@Composable
fun ThinBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).background(MaterialTheme.podcastColors.track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** A circular arrow with the number of seconds inside, for skip back and skip forward. */
@Composable
fun SkipIcon(seconds: Int, forward: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val description = if (forward) "Forward $seconds seconds" else "Back $seconds seconds"
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Replay,
            contentDescription = null,
            modifier = Modifier.size(size).graphicsLayer { if (forward) scaleX = -1f },
        )
        Text(
            "$seconds",
            fontSize = (size.value * 0.3f).sp,
            lineHeight = (size.value * 0.3f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = (size.value * 0.12f).dp),
        )
    }
}

// ------------------------------------------------------------------ navigation

/** The bottom tab bar: a hairline above, the selected tab in the accent. */
@Composable
fun PaperNavBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(64.dp), content = content)
    }
}

@Composable
fun RowScope.PaperNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontFamily = Gothic,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

/** A plain row with a label, an optional hint under it, and something at the end. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .height(if (hint != null) 40.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Gap the width of the screen's side margin, for lining things up with [ScreenHeader]. */
val SideMargin = 24.dp
