package com.tvlauncher.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tvlauncher.app.data.AppEntry

private val CardShape = RoundedCornerShape(12.dp)

/**
 * Tarjeta de app estilo Apple TV: 16:9, escala al enfocar, borde blanco fino.
 * El efecto de foco usa graphicsLayer (solo fase de dibujo, sin re-layout) → 60 fps en SoC modestos.
 * Sin sombras difusas ni blur (caros en GPUs de tvbox).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppCard(
    app: AppEntry,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier) {
        Surface(
            onClick = { onClick(app) },
            onLongClick = { onLongClick(app) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(CardShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LauncherColors.Card,
                focusedContainerColor = LauncherColors.CardFocused,
                pressedContainerColor = LauncherColors.CardFocused,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CardShape),
            ),
        ) {
            AppImage(app)
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = LauncherColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun AppImage(app: AppEntry) {
    val loader = LocalIconLoader.current
    // peek síncrono: si ya está en caché, el primer frame ya trae el icono (sin parpadeo al hacer scroll).
    val icon by produceState(initialValue = loader.peek(app.key), app.key) {
        if (value == null) value = loader.get(app)
    }
    val img = icon
    when {
        img == null -> Box(Modifier.fillMaxSize())
        img.isBanner -> Image(
            bitmap = img.image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        else -> Box(
            Modifier
                .fillMaxSize()
                .background(LauncherColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = img.image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight(0.62f)
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
