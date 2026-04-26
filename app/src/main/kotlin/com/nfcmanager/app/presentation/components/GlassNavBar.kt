package com.nfcmanager.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.navigation.TopDestination
import com.nfcmanager.app.presentation.theme.LocalAppColors

/**
 * Bottom navigation: **solid** brand bar, sliding selected pill, icon over label.
 * Bar height, padding, and icon size scale gently with screen width (360dp baseline).
 */
@Composable
fun GlassNavBar(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val barBase = C.surface

    var pending by remember { mutableStateOf(current) }
    LaunchedEffect(current) { pending = current }

    val items = TopDestination.BottomNavDestinations
    val selectedIndex = items.indexOf(pending).coerceAtLeast(0)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val w = maxWidth
        val barOuterHorizontal = maxOf(12.dp, minOf(20.dp, w * 0.045f))
        val contentHeight = maxOf(48.dp, minOf(64.dp, w * 0.14f))
        val innerHorizontal = maxOf(2.dp, minOf(6.dp, w * 0.01f))
        val innerVertical = maxOf(2.dp, minOf(8.dp, w * 0.012f))
        val itemGap = maxOf(2.dp, minOf(8.dp, w * 0.015f))
        val iconSize = maxOf(18.dp, minOf(24.dp, w * 0.055f))
        val iconLabelGap = maxOf(2.dp, minOf(6.dp, w * 0.012f))
        val barCorner = maxOf(24.dp, minOf(32.dp, contentHeight * 0.5f))
        val barShape = RoundedCornerShape(barCorner)

        val sectionShadowTint = Color.Black.copy(alpha = 0.06f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = barOuterHorizontal)
                .shadow(
                    elevation = 3.dp,
                    shape = barShape,
                    ambientColor = sectionShadowTint,
                    spotColor = sectionShadowTint,
                    clip = false,
                )
                .clip(barShape)
                .background(barBase)
                .border(
                    width = 1.dp,
                    color = C.outlineVariant.copy(alpha = 0.55f),
                    shape = barShape,
                ),
        ) {

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .padding(
                        horizontal = innerHorizontal,
                        vertical = innerVertical,
                    ),
            ) {
                val totalWidth = maxWidth
                val itemCount = items.size
                val itemWidth = (totalWidth - itemGap * (itemCount - 1)) / itemCount

                val targetOffset = (itemWidth + itemGap) * selectedIndex
                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "glassNavPillOffset",
                )

                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(C.navBarSelectedContainer),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { dest ->
                        GlassNavItem(
                            selected = dest == pending,
                            icon = dest.icon,
                            label = dest.label,
                            iconSize = iconSize,
                            iconLabelGap = iconLabelGap,
                            itemClipShape = barShape,
                            onClick = {
                                if (dest != pending) {
                                    pending = dest
                                    onSelect(dest)
                                }
                            },
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    iconLabelGap: Dp,
    /** Matches outer nav bar radius so the pill and slots align visually. */
    itemClipShape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "glassNavPress",
    )
    val selectScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "glassNavSelectScale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) C.navBarSelectedContent else C.navBarUnselectedContent,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassNavTint",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassNavLabelFade",
    )

    Box(
        modifier = modifier
            .scale(pressScale * selectScale)
            .clip(itemClipShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.height(iconLabelGap))
            Text(
                text = label,
                color = contentColor.copy(alpha = labelAlpha),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
