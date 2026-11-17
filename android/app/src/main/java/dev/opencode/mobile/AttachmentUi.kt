package dev.opencode.mobile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AttachmentPreview(file: ChatAttachment, computer: Computer?, directory: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewing by remember(file.uri) { mutableStateOf(false) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }
    var opening by remember(file.uri) { mutableStateOf(false) }
    var finished by remember(file.uri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, file.uri, computer?.id, directory) {
        if (file.image) {
            try { value = AttachmentFiles.bitmap(context, file, computer, directory, 600) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            finally { finished = true }
        }
    }
    OutlinedCard(onClick = {
        if (file.image) viewing = true else if (!opening) scope.launch {
            opening = true
            try { AttachmentFiles.open(context, file, computer, directory) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not open file." }
            finally { opening = false }
        }
    }, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        if (file.image) Box(Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Open image: ${file.name}", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit) }
                ?: if (!finished) CircularProgressIndicator(Modifier.padding(24.dp).size(24.dp)) else Column(Modifier.padding(16.dp)) {
                    Icon(AppIcons.Image, contentDescription = null)
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Tap to load image", style = MaterialTheme.typography.labelSmall)
                }
        } else Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (opening) CircularProgressIndicator(Modifier.size(22.dp)) else Icon(AppIcons.Attach, contentDescription = "Open file")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(file.mime, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (viewing) ImageViewer(file, computer, directory) { viewing = false }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Attachment") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun ImageViewer(file: ChatAttachment, computer: Computer?, directory: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(null, file.uri) {
        try { value = AttachmentFiles.bitmap(context, file, computer, directory, 2400); if (value == null) error = "This image format cannot be displayed." }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not load image." }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { scale, pan, _ -> zoom = (zoom * scale).coerceIn(1f, 6f); offset = if (zoom > 1f) offset + pan else Offset.Zero }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    ActionIcon(AppIcons.Close, "Close image", onClose)
                    Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().transformable(transform), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
                    ?: if (error != null) Text(error.orEmpty(), Modifier.padding(24.dp), color = Color.White) else CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
