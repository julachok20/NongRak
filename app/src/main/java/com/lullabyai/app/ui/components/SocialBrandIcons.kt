package com.lullabyai.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lullabyai.app.R

@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo_google),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(22.dp)
    )
}

/** Green LINE bubble on a white disc so it stays readable on the brand-green button. */
@Composable
fun LineLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_line),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp)
        )
    }
}
