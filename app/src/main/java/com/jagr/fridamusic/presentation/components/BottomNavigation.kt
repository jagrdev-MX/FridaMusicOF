package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.presentation.theme.LiquidOnSurfaceVariant
import com.jagr.fridamusic.presentation.theme.LiquidPrimary
import com.jagr.fridamusic.presentation.theme.LiquidTypography

@Composable
fun BottomNavigation(
    modifier: Modifier = Modifier,
    isCollapsed: Boolean,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onExpandPlayer: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) +
                        scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f)) +
                        slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f))) togetherWith
                        (fadeOut(animationSpec = tween(200)) +
                                scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f)) +
                                slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f))) using
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ ->
                            spring(dampingRatio = 0.75f, stiffness = 200f)
                        })
            },
            label = "nav_animation"
        ) { collapsed ->
            if (collapsed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { onNavigate("home") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = LiquidPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    MiniPlayer(
                        modifier = Modifier.weight(1f),
                        onExpand = onExpandPlayer
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MiniPlayer(onExpand = onExpandPlayer)

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavItem(
                                icon = Icons.Default.Home,
                                label = "Home",
                                isSelected = currentRoute == "home",
                                onClick = { onNavigate("home") }
                            )

                            NavItem(
                                icon = Icons.Default.Search,
                                label = "Search",
                                isSelected = currentRoute == "search",
                                onClick = { onNavigate("search") }
                            )

                            NavItem(
                                icon = Icons.Default.LibraryMusic,
                                label = "Library",
                                isSelected = currentRoute == "library",
                                onClick = { onNavigate("library") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color: Color = if (isSelected) LiquidPrimary else LiquidOnSurfaceVariant

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            style = LiquidTypography.labelSmall
        )
    }
}