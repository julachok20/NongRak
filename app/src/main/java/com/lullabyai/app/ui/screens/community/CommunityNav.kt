package com.lullabyai.app.ui.screens.community

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Which community tab a screen is showing. */
enum class CommunityTab { FEED, GROUP, PROFILE }

/**
 * The community footer: a floating glass pill, NongRak | Feed | Group | Profile (mockup 53).
 *
 * Floating rather than docked - it sits above the content with a margin all round, so the feed
 * scrolls underneath it. Each tab carries its own colour from CommunityPalette instead of the
 * single app blue.
 *
 * "Glass" here is a translucent surface with a light gradient and a hairline border, not a real
 * backdrop blur: Modifier.blur and RenderEffect need API 31, and this app supports API 26. A
 * blur would silently do nothing on a third of the devices this ships to, so the effect is built
 * from things that work everywhere.
 *
 * The caller must let content draw behind it - pass the Scaffold's padding but do not consume the
 * bottom inset, or the pill will float over a blank strip instead of over the feed.
 */
@Composable
fun CommunityBottomBar(
    selected: CommunityTab,
    isThai: Boolean,
    onExitToApp: () -> Unit,
    onFeed: () -> Unit,
    onGroup: () -> Unit,
    onProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    // The glass: a translucent wash over the surface colour, brighter at the top
                    // edge the way light catches a curved pane.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    icon = Icons.Filled.Home,
                    label = if (isThai) "หน้าหลัก" else "NongRak",
                    accent = CommunityPalette.Home,
                    selected = false,
                    modifier = Modifier.weight(1f),
                    onClick = onExitToApp
                )
                NavItem(
                    icon = Icons.Filled.DynamicFeed,
                    label = "Feed",
                    accent = CommunityPalette.Feed,
                    selected = selected == CommunityTab.FEED,
                    modifier = Modifier.weight(1f),
                    onClick = onFeed
                )
                NavItem(
                    icon = Icons.Filled.Groups,
                    label = if (isThai) "กลุ่ม" else "Group",
                    accent = CommunityPalette.Group,
                    selected = selected == CommunityTab.GROUP,
                    modifier = Modifier.weight(1f),
                    onClick = onGroup
                )
                NavItem(
                    icon = Icons.Filled.Person,
                    label = if (isThai) "โปรไฟล์" else "Profile",
                    accent = CommunityPalette.Profile,
                    selected = selected == CommunityTab.PROFILE,
                    modifier = Modifier.weight(1f),
                    onClick = onProfile
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outline,
        label = "navTint"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 40.dp else 30.dp,
        animationSpec = spring(),
        label = "navPill"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(28.dp)
                .clip(CircleShape)
                .background(if (selected) CommunityPalette.soft(accent) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
