package dev.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class MainActivity : ComponentActivity() {
    private val model: MobileModel by viewModels()
    override fun onStart() { super.onStart(); model.active(true) }
    override fun onStop() { model.active(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8CE8BD))) {
                Surface(Modifier.fillMaxSize()) {
                    val state by model.state.collectAsStateWithLifecycle()
                    // Pairing secrets intentionally use remember, never rememberSaveable.
                    var link by remember { mutableStateOf("") }
                    var name by rememberSaveable { mutableStateOf("") }
                    var pairing by remember { mutableStateOf(false) }
                    var confirmation by remember { mutableStateOf<PairingLink?>(null) }
                    var directory by rememberSaveable { mutableStateOf("") }
                    var creating by remember { mutableStateOf(false) }
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
                        Text("OpenCode", style = MaterialTheme.typography.headlineLarge)
                        Text(state.status, color = MaterialTheme.colorScheme.primary)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pairing = true }) { Text("+ Computer") }
                            if (state.computer != null) TextButton(onClick = { model.refresh() }, enabled = !state.busy) { Text("Refresh") }
                        }
                        if (state.computers.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }) { Text(state.computer?.name ?: "Choose computer") }
                                DropdownMenu(expanded, { expanded = false }) {
                                    state.computers.forEach { computer ->
                                        DropdownMenuItem(text = { Text(computer.name) }, onClick = {
                                            expanded = false; model.connect(computer)
                                        })
                                    }
                                }
                            }
                        }
                        if (state.computer == null) {
                            Spacer(Modifier.height(32.dp))
                            Text("Your PC, in your pocket.", style = MaterialTheme.typography.headlineSmall)
                            Text("On your PC run:\nopencode pair --remote\n\nScan its QR code or paste the HTTPS pairing link. Your PC must stay awake and online.", Modifier.padding(top = 16.dp))
                        } else if (state.session == null) {
                            Row {
                                TextButton(onClick = { creating = true }, enabled = !state.busy) { Text("+ Session") }
                                TextButton(onClick = { model.forget(state.computer!!) }, enabled = !state.busy) { Text("Forget computer") }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.sessions, key = { it.getString("id") }) { session ->
                                    Card(onClick = { model.select(session) }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(session.optString("title").ifBlank { "Untitled session" }, style = MaterialTheme.typography.titleMedium)
                                            Text(session.optJSONObject("location")?.optString("directory").orEmpty(), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                if (state.nextSessions != null) item { TextButton(onClick = { model.moreSessions() }, enabled = !state.busy) { Text("Load more sessions") } }
                            }
                        } else {
                            TextButton(onClick = { model.select(null) }) { Text("‹ Sessions") }
                            Text(state.session!!.optString("title").ifBlank { "Conversation" }, style = MaterialTheme.typography.titleMedium)
                            TabRow(selectedTabIndex = tab) {
                                listOf("Chat", "Approvals (${state.permissions.size})", "Changes").forEachIndexed { index, title ->
                                    Tab(selected = tab == index, onClick = { tab = index; if (index == 2) model.loadDiff() }, text = { Text(title) })
                                }
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                                when (tab) {
                                    0 -> {
                                        if (state.nextMessages != null) item { TextButton(onClick = { model.moreMessages() }, enabled = !state.busy) { Text("Load older messages") } }
                                        items(state.messages, key = { it.getString("id") }) { message ->
                                            Card(Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(message.optString("type").uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                                    SelectionContainer { Text(message.displayText(), fontFamily = FontFamily.Monospace) }
                                                }
                                            }
                                        }
                                    }
                                    1 -> {
                                        if (state.permissions.isEmpty()) item { Text("No pending approvals") }
                                        items(state.permissions, key = { it.getString("id") }) { permission ->
                                            Card(Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(permission.optString("action"), style = MaterialTheme.typography.titleMedium)
                                                    SelectionContainer { Text(permission.toString(2), fontFamily = FontFamily.Monospace) }
                                                    Row {
                                                        TextButton(enabled = !state.busy, onClick = { model.reply(permission.getString("id"), "reject") }) { Text("Reject") }
                                                        Button(enabled = !state.busy, onClick = { model.reply(permission.getString("id"), "once") }) { Text("Allow once") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    2 -> {
                                        item { TextButton(onClick = { model.loadDiff() }, enabled = !state.busy) { Text("Refresh changes") } }
                                        if (state.diffs.isEmpty()) item { Text("No changes loaded") }
                                        items(state.diffs, key = { it.getString("file") }) { diff ->
                                            Text(diff.optString("file"), style = MaterialTheme.typography.titleSmall)
                                            Text("+${diff.optInt("additions")} −${diff.optInt("deletions")}", color = MaterialTheme.colorScheme.primary)
                                            SelectionContainer { Text(diff.optString("patch"), fontFamily = FontFamily.Monospace) }
                                        }
                                    }
                                }
                            }
                            val draft = state.drafts[state.session!!.getString("id")].orEmpty()
                            OutlinedTextField(value = draft, onValueChange = model::draft, modifier = Modifier.fillMaxWidth(), label = { Text("Ask OpenCode…") }, maxLines = 5)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { model.send() }, enabled = !state.busy && draft.isNotBlank()) { Text("Send") }
                                OutlinedButton(onClick = { model.interrupt() }, enabled = !state.busy) { Text("Stop") }
                            }
                        }
                    }
                    if (pairing) AlertDialog(onDismissRequest = { pairing = false; link = "" }, title = { Text("Connect your computer") }, text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Run opencode pair --remote on your PC.")
                            OutlinedTextField(name, { name = it }, label = { Text("Computer name") })
                            OutlinedTextField(link, { link = it }, label = { Text("HTTPS pairing link") })
                            OutlinedButton(onClick = {
                                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
                                GmsBarcodeScanning.getClient(this@MainActivity, options).startScan()
                                    .addOnSuccessListener { barcode -> link = barcode.rawValue.orEmpty() }
                                    .addOnFailureListener { model.report("Scanner unavailable. Install Google Play services or paste the link.") }
                            }) { Text("Scan QR code") }
                        }
                    }, confirmButton = { Button(enabled = !state.busy, onClick = {
                        try { confirmation = PairingLink.parse(link) } catch (e: Exception) { model.report(e.message ?: "Invalid link") }
                    }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { pairing = false; link = "" }) { Text("Cancel") } })
                    confirmation?.let { target ->
                        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Trust this server?") }, text = {
                            Text("${target.origin}\n\nOnly connect to a computer you trust. This app will be able to run OpenCode actions on it.")
                        }, confirmButton = { Button(onClick = {
                            model.pair(link, name); confirmation = null; pairing = false; link = ""
                        }) { Text("Connect") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
                    }
                    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("New session") }, text = {
                        OutlinedTextField(directory, { directory = it }, label = { Text("Absolute directory on PC") })
                    }, confirmButton = { Button(onClick = { model.create(directory); creating = false }, enabled = directory.isNotBlank() && !state.busy) { Text("Create") } }, dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
                    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("OpenCode") }, text = { Text(error) }, confirmButton = { TextButton(onClick = model::clearError) { Text("OK") } }) }
                }
            }
        }
    }
}
