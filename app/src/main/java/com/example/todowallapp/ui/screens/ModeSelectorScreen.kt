package com.example.todowallapp.ui.screens

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.R
import com.example.todowallapp.data.model.AppMode
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations

private val FocusHaloEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun ModeSelectorScreen(
    onSelectMode: (AppMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val wall = LocalWallColors.current
    val isLandscape =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val horizontalPad: Dp = if (isLandscape) 80.dp else 40.dp
    val verticalPad: Dp = if (isLandscape) 36.dp else 48.dp
    val titleGap: Dp = if (isLandscape) 56.dp else 88.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(wall.surfaceBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionDown, Key.DirectionRight -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(2)
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        when (selectedIndex) {
                            0 -> onSelectMode(AppMode.WALL)
                            1 -> onSelectMode(AppMode.PHONE)
                            2 -> onSignOut()
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = horizontalPad, vertical = verticalPad)
    ) {
        WordmarkRow()

        Spacer(modifier = Modifier.height(titleGap))

        Text(
            text = "Choose your mode",
            style = MaterialTheme.typography.headlineLarge.copy(
                letterSpacing = (-0.28).sp
            ),
            color = wall.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can switch anytime from settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = wall.textSecondary
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ModeCard(
                    title = "Wall",
                    blurb = "Always-on board. Read from across the room.",
                    meta = "ENCODER NAVIGATION",
                    glyphRes = R.drawable.ic_mode_wall,
                    isFocused = selectedIndex == 0,
                    onClick = { onSelectMode(AppMode.WALL) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    title = "Phone",
                    blurb = "Capture-first with voice. Speak it once and forget.",
                    meta = "TOUCH · VOICE",
                    glyphRes = R.drawable.ic_mode_phone,
                    isFocused = selectedIndex == 1,
                    onClick = { onSelectMode(AppMode.PHONE) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ModeCard(
                    title = "Wall",
                    blurb = "Always-on board. Read from across the room.",
                    meta = "ENCODER NAVIGATION",
                    glyphRes = R.drawable.ic_mode_wall,
                    isFocused = selectedIndex == 0,
                    onClick = { onSelectMode(AppMode.WALL) },
                    modifier = Modifier.fillMaxWidth()
                )
                ModeCard(
                    title = "Phone",
                    blurb = "Capture-first with voice. Speak it once and forget.",
                    meta = "TOUCH · VOICE",
                    glyphRes = R.drawable.ic_mode_phone,
                    isFocused = selectedIndex == 1,
                    onClick = { onSelectMode(AppMode.PHONE) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SignOutPill(
                isFocused = selectedIndex == 2,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun WordmarkRow() {
    val wall = LocalWallColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_ledger_mark),
            contentDescription = null,
            tint = wall.accentPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Ledger",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.36.sp
            ),
            color = wall.textPrimary
        )
        Spacer(modifier = Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(wall.dividerColor)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "A quieter place to keep what's on your mind",
            style = MaterialTheme.typography.bodySmall.copy(
                letterSpacing = 0.56.sp
            ),
            color = wall.textMuted
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    blurb: String,
    meta: String,
    glyphRes: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wall = LocalWallColors.current
    val cardShape = RoundedCornerShape(20.dp)
    val borderTone = if (isFocused) wall.borderFocused else wall.borderColor

    Box(
        modifier = modifier
            .heightIn(min = 168.dp)
            .focusHalo(
                color = wall.accentPrimary,
                cornerRadius = 20.dp,
                radius = 24.dp,
                spread = 2.dp,
                alpha = 0.55f,
                visible = isFocused
            )
            .background(color = wall.surfaceCard, shape = cardShape)
            .border(width = 1.dp, color = borderTone, shape = cardShape)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 22.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = wall.accentPrimary.copy(alpha = if (wall.isDark) 0.15f else 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(glyphRes),
                    contentDescription = null,
                    tint = wall.accentPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = wall.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = wall.textSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = wall.textMuted
            )
        }
    }
}

@Composable
private fun SignOutPill(
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val wall = LocalWallColors.current
    val pillShape = CircleShape
    val contentColor = if (isFocused) wall.urgencyOverdue else wall.textMuted
    val bgColor = if (isFocused) wall.surfaceCard else Color.Transparent
    val borderTone = if (isFocused) wall.borderColor else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .focusHalo(
                color = wall.urgencyOverdue,
                cornerRadius = 999.dp,
                radius = 20.dp,
                spread = 1.dp,
                alpha = 0.45f,
                visible = isFocused
            )
            .background(color = bgColor, shape = pillShape)
            .border(width = 1.dp, color = borderTone, shape = pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_signout),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Sign out",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Modifier.focusHalo(
    color: Color,
    cornerRadius: Dp,
    radius: Dp,
    spread: Dp,
    alpha: Float,
    visible: Boolean
): Modifier {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) alpha else 0f,
        animationSpec = tween(
            durationMillis = WallAnimations.SHORT,
            easing = FocusHaloEasing
        ),
        label = "focusHaloAlpha"
    )
    return this.drawBehind {
        if (animatedAlpha <= 0f) return@drawBehind
        val r = radius.toPx()
        val s = spread.toPx()
        val cornerPx = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color.copy(alpha = animatedAlpha)
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(
                left = -s,
                top = -s,
                right = size.width + s,
                bottom = size.height + s,
                radiusX = cornerPx,
                radiusY = cornerPx,
                paint = paint
            )
        }
    }
}
