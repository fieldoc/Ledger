package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPageLabel: Boolean = false
) {
    if (pageCount <= 1) return
    val safeCurrentPage = currentPage.coerceIn(0, pageCount - 1)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(pageCount) { index ->
            val isSelected = safeCurrentPage == index
            val pillWidth by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 14.dp,
                animationSpec = tween(WallAnimations.SHORT),
                label = "pageIndicatorWidth"
            )
            val pillAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.72f,
                animationSpec = tween(WallAnimations.SHORT),
                label = "pageIndicatorAlpha"
            )
            val pillColor by animateColorAsState(
                targetValue = if (isSelected) LocalWallColors.current.accentPrimary else LocalWallColors.current.textPrimary,
                animationSpec = tween(WallAnimations.SHORT),
                label = "pageIndicatorColor"
            )

            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(14.dp)
                    .alpha(pillAlpha)
                    .clip(CircleShape)
                    .background(pillColor)
                    .semantics {
                        contentDescription = "Page ${index + 1} of $pageCount"
                    }
                    .clickable(role = Role.Button) {
                        if (!isSelected) onPageSelected(index)
                    }
            )
        }

        if (showPageLabel) {
            Text(
                text = "${safeCurrentPage + 1}/$pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = LocalWallColors.current.textSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun PageIndicatorPreview() {
    LedgerTheme {
        PageIndicator(
            pageCount = 6,
            currentPage = 2,
            onPageSelected = {},
            showPageLabel = true
        )
    }
}

