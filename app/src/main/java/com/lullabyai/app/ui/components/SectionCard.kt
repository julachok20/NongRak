package com.lullabyai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.ui.theme.Primary

/**
 * The house card: a flat, bordered panel with an icon-tiled heading.
 *
 * This is the ข้อมูลลูก (child info) look, lifted from the pattern those screens already use so the
 * community matches it rather than approximating it:
 *
 *   * 18.dp corners, surface background
 *   * a 1.dp outlineVariant border at half alpha - NOT elevation. Flat and bordered is what makes
 *     the child pages read as clean; a shadow under every card is what made the community heavy.
 *   * a 30.dp rounded tile behind the icon, washed with the section's own colour at 12%
 *   * titleSmall bold, with an optional labelSmall subtitle in onSurfaceVariant
 *
 * [accent] lets each section carry its own colour while the shape stays identical, which is how
 * the page stays colourful without becoming a patchwork.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = Primary,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(2.dp))
        content()
    }
}

/** The small rounded pill the child pages use for counts and statuses. */
@Composable
fun SectionPill(label: String, accent: Color = Primary) {
    Surface(shape = RoundedCornerShape(100.dp), color = accent.copy(alpha = 0.10f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
