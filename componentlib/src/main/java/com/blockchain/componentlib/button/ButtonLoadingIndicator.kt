package com.blockchain.componentlib.button

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.blockchain.componentlib.R
import com.blockchain.componentlib.theme.AppTheme

@Composable
fun ButtonLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val rotation by rememberInfiniteTransition()
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = InfiniteRepeatableSpec(
                TweenSpec(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )
        )
    Image(
        painter = painterResource(R.drawable.ic_loading),
        contentDescription = null,
        modifier = modifier.rotate(rotation)
    )
}

@Preview
@Composable
fun ButtonLoadingIndicatorPreview() {
    AppTheme {
        Surface {
            ButtonLoadingIndicator()
        }
    }
}
