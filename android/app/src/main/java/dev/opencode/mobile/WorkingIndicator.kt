package dev.opencode.mobile

import android.animation.ValueAnimator
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WorkingIndicator(dark: Boolean) {
    val modifier = Modifier.size(30.dp).semantics { contentDescription = "Working" }
    if (!ValueAnimator.areAnimatorsEnabled()) Icon(AppIcons.Refresh, null, modifier)
    else if (Build.VERSION.SDK_INT >= 28) WorkingBot(dark, modifier)
    else CircularProgressIndicator(modifier, strokeWidth = 2.dp)
}

@RequiresApi(28)
@Composable
private fun WorkingBot(dark: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val drawable = remember(dark, context) {
        val res = if (dark) R.drawable.working_bot_dark else R.drawable.working_bot_light
        runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, res)) }.getOrNull()
    }
    if (drawable == null) {
        CircularProgressIndicator(modifier, strokeWidth = 2.dp)
        return
    }
    AndroidView(factory = { ImageView(it).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Working"
    } },
        modifier = modifier, update = { it.setImageDrawable(drawable); (drawable as? AnimatedImageDrawable)?.start() })
    DisposableEffect(drawable) { onDispose { (drawable as? AnimatedImageDrawable)?.stop() } }
}
