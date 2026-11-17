package dev.opencode.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeConversation(session: JSONObject, busy: Boolean, onOpen: () -> Unit, onDelete: () -> Unit,
    working: Boolean = false, dark: Boolean = false) {
    val currentBusy by rememberUpdatedState(busy)
    val currentDelete by rememberUpdatedState(onDelete)
    // Keep the physical swipe direction the same even on an RTL device.
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dismissDirection = if (rightToLeft) SwipeToDismissBoxValue.StartToEnd else SwipeToDismissBoxValue.EndToStart
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { target ->
        if (target == dismissDirection && !currentBusy) currentDelete()
        false // Keep the row until the user confirms and the server acknowledges deletion.
    })
    SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = rightToLeft && !busy,
        enableDismissFromEndToStart = !rightToLeft && !busy,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = if (rightToLeft) Alignment.CenterStart else Alignment.CenterEnd) {
                Icon(AppIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics {
            customActions = listOf(CustomAccessibilityAction("Delete conversation") { if (!busy) onDelete(); !busy })
        }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.optString("title").ifBlank { "Untitled session" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    session.optJSONObject("location")?.optString("directory")?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (working) WorkingIndicator(dark)
            }
        }
    }
}
