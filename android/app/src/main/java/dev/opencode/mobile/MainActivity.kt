package dev.opencode.mobile

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
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
        enableEdgeToEdge()
        // Compose handles IME insets. Prevent the window from also panning the entire chat.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var themeMode by remember {
                mutableStateOf(ThemeMode.fromStored(appearance.getString("theme", null),
                    if (appearance.contains("dark")) appearance.getBoolean("dark", false) else null))
            }
            val dark = themeMode.isDark(systemDark)
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            OpenCodeTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop(dark)
                    val state by model.state.collectAsStateWithLifecycle()
                    val connected = state.status == PeerStatus.CONNECTED.label
                    val sessionId = state.session?.optString("id")
                    val terminalActivity = sessionId?.let {
                        rememberTerminalActivity(state.computer?.id, it, state.messages, connected,
                            model::terminalSnapshot, state.terminalEvents)
                    }
                    val activeProcesses = if (connected) terminalActivity?.records.orEmpty().count { it.phase.active } else 0
                    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) model.attach(uris) }
                    // Pairing secrets intentionally use remember, never rememberSaveable.
                    var link by remember { mutableStateOf("") }
                    var name by rememberSaveable { mutableStateOf("") }
                    var pairing by remember { mutableStateOf(false) }
                    var confirmation by remember { mutableStateOf<PairingLink?>(null) }
                    var lanAddress by remember { mutableStateOf(false) }
                    var computerIp by rememberSaveable { mutableStateOf("") }
                    var lanError by remember { mutableStateOf<String?>(null) }
                    var sameWifi by rememberSaveable { mutableStateOf(false) }
                    var pairingError by remember { mutableStateOf<String?>(null) }
                    val reviewLink: () -> Unit = {
                        try {
                            pairingError = null
                            link = PairingLink.decodeQr(link, preferVpn = hasActiveVpn(this@MainActivity))
                            if (sameWifi && computerIp.isNotBlank()) {
                                confirmation = PairingLink.forLan(link, computerIp)
                            } else if (sameWifi || PairingLink.needsLanAddress(link)) {
                                lanError = null
                                lanAddress = true
                            } else confirmation = PairingLink.parse(link)
                            pairing = false
                        } catch (e: Exception) {
                            pairing = true
                            pairingError = e.message ?: "This QR is not a pairing link. Scan the QR shown by OpenCode, or paste its link."
                            android.util.Log.i("PairingFlow", "pairing_input_rejected: type=${e.javaClass.simpleName} empty=${link.isBlank()} http=${link.trim().startsWith("http", ignoreCase = true)}")
                        }
                    }
                    var deletingSession by remember { mutableStateOf<org.json.JSONObject?>(null) }
                    var forgetting by remember { mutableStateOf<Computer?>(null) }
                    var settings by rememberSaveable { mutableStateOf(false) }
                    var computerSwitcher by remember { mutableStateOf(false) }
                    var renaming by remember { mutableStateOf<Computer?>(null) }
                    var editedComputerName by remember { mutableStateOf("") }
                    var modelPicker by remember { mutableStateOf(false) }
                    var projectFolder by remember { mutableStateOf<String?>(null) }
                    val showProjectFolder: () -> Unit = { modelPicker = false; projectFolder = "" }
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    val scanQr: () -> Unit = {
                                android.util.Log.i("PairingFlow", "scanner_open")
                                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
                                GmsBarcodeScanning.getClient(this@MainActivity, options).startScan()
                                    .addOnSuccessListener { barcode ->
                                        android.util.Log.i("PairingFlow", "scanner_result_received")
                                        link = barcode.url?.url?.takeIf { it.isNotBlank() }
                                            ?: barcode.rawValue?.takeIf { it.isNotBlank() }
                                            ?: barcode.rawBytes?.toString(Charsets.UTF_8).orEmpty()
                                        android.util.Log.i("PairingFlow", "scanner_payload: empty=${link.isBlank()} hasUrl=${barcode.url?.url != null} hasRaw=${!barcode.rawValue.isNullOrBlank()} hasBytes=${barcode.rawBytes != null}")
                                        reviewLink()

                                    }
                                    .addOnCanceledListener { android.util.Log.i("PairingFlow", "scanner_cancelled") }
                                    .addOnFailureListener { failure ->
                                        val code = (failure as? com.google.mlkit.common.MlKitException)?.errorCode
                                        android.util.Log.w("PairingFlow", "scanner_failed: code=$code type=${failure.javaClass.simpleName}")
                                        model.report("QR scanner could not start${code?.let { " (code $it)" }.orEmpty()}. Check Google Play services and try again, or paste the pairing link.")
                                    }
                    }
                    BackHandler(enabled = !settings && state.session != null && !pairing && !lanAddress && confirmation == null && deletingSession == null && !computerSwitcher) { model.select(null) }
                    BackHandler(enabled = settings && !pairing && !lanAddress && confirmation == null && renaming == null && forgetting == null && !computerSwitcher) { settings = false }
                    LaunchedEffect(state.session?.optString("id")) { tab = 0 }
                    if (settings) SettingsScreen(state, themeMode,
                        onThemeChange = { value -> themeMode = value; appearance.edit().putString("theme", value.preference).remove("dark").apply() },
                        onBack = { settings = false }, onSelect = model::connect,
                        onAdd = { pairing = true },
                        onRename = { computer -> editedComputerName = computer.name; renaming = computer },
                        onRemove = { forgetting = it }, onReconnect = model::reconnect,
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp))
                    else Column(Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (state.session == null) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComputerSelector(state, { computerSwitcher = true }, Modifier.weight(1f))
                            ActionIcon(AppIcons.Settings, "Settings", { settings = true })
                        }
                        else Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(AppIcons.Back, "Back to chats", { model.select(null) })
                            Column(Modifier.weight(1f).clickable { computerSwitcher = true }.padding(horizontal = 4.dp)) {
                                Text(state.session!!.optString("title").ifBlank { "Conversation" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { ConnectionLabel(state, showComputer = true) }
                                    Icon(AppIcons.ChevronDown, contentDescription = "Switch computer", modifier = Modifier.size(14.dp))
                                }
                            }
                            listOf(AppIcons.Chat, AppIcons.Shield, AppIcons.Code, AppIcons.Terminal).forEachIndexed { index, icon ->
                                val label = when {
                                    index == 3 && activeProcesses == 1 -> "Live terminal, 1 active process"
                                    index == 3 && activeProcesses > 1 -> "Live terminal, $activeProcesses active processes"
                                    else -> listOf("Chat", "Approvals", "Changes", "Live terminal")[index]
                                }
                                Box {
                                    ActionIcon(icon, label, { tab = index; if (index == 2) model.loadDiff() }, modifier = Modifier.size(44.dp), filled = tab == index)
                                    if (index == 1 && state.permissions.isNotEmpty()) Badge(Modifier.align(Alignment.TopEnd)) { Text(state.permissions.size.toString()) }
                                    if (index == 3 && activeProcesses > 0) Badge(Modifier.align(Alignment.TopEnd))
                                }
                            }
                        }
                        ConnectionWarning(state, model::reconnect)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.computer == null) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 20.dp)) {
                                WelcomePanel(state.computers.isNotEmpty(), state.busy, scanQr, { pairing = true })
                            }
                        } else if (state.session == null) {
                            ConversationHome(state, dark, onNew = { model.create() }, onRefresh = { model.refresh() },
                                onSearch = model::searchConversations, onRetrySearch = model::retrySearch,
                                onMore = {
                                    if (state.search.query.isBlank()) model.moreSessions() else model.moreSearchResults()
                                },
                                onOpen = model::select, onDelete = { deletingSession = it },
                                onToggleHistory = model::toggleHistory, onClearHistory = model::clearHomeHistory,
                                onRestoreHistory = model::restoreHomeHistory, onSelectProject = model::selectProject,
                                onCreateProject = model::createProject, modifier = Modifier.weight(1f))
                        } else {
                            if (tab == 0) key(state.session!!.optString("id")) {
                                ChatTimeline(state, model::moreMessages, Modifier.weight(1f).fillMaxWidth())
                            } else if (tab == 3 && terminalActivity != null) {
                                key(state.computer?.id, sessionId) {
                                    LiveTerminalPanel(
                                        activity = terminalActivity, computer = state.computer, connected = connected,
                                        canInterrupt = !state.interrupting && WorkKind.RESPONSE in state.workingSessions[sessionId].orEmpty(),
                                        onInterrupt = model::interrupt, fetchSnapshot = model::terminalSnapshot,
                                        canLoadEarlier = !state.busy && state.nextMessages != null, onLoadEarlier = model::moreMessages,
                                        modifier = Modifier.weight(1f).fillMaxWidth(), directory = state.session?.workDirectory(),
                                    )
                                }
                            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                                when (tab) {
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
                                            Text("+${diff.optInt("additions")} / -${diff.optInt("deletions")}", color = MaterialTheme.colorScheme.primary)
                                            SelectionContainer { Text(diff.optString("patch"), fontFamily = FontFamily.Monospace) }
                                        }
                                    }
                                }
                            }
                            if (tab == 0) ChatComposer(state, model::draft, model::send, model::interrupt, { modelPicker = true; model.loadModels() }, { filePicker.launch(arrayOf("image/*")) }, { filePicker.launch(arrayOf("*/*")) }, model::removeAttachment, showProjectFolder, model::loadComposerCatalog)

                        }
                    }
                    if (modelPicker && state.session != null) ChatModelPicker(state, { modelPicker = false }, model::loadModels, showProjectFolder, { selected, effort -> model.chooseModel(selected, effort); modelPicker = false })
                    projectFolder?.let { folder ->
                        AlertDialog(
                            onDismissRequest = { projectFolder = null },
                            title = { Text("Change project folder") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Enter the full path to an existing folder on your PC. This chat keeps its history and uses the new folder for future work.")
                                    OutlinedTextField(folder, { projectFolder = it }, label = { Text("Folder on the PC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                }
                            },
                            confirmButton = { Button(enabled = folder.isNotBlank() && !state.busy, onClick = { projectFolder = null; model.moveProject(folder) }) { Text("Move chat") } },
                            dismissButton = { TextButton(onClick = { projectFolder = null }) { Text("Cancel") } },
                        )
                    }
                    renaming?.let { computer ->
                        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename computer") }, text = {
                            OutlinedTextField(editedComputerName, { editedComputerName = it }, label = { Text("Computer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }, confirmButton = { Button(enabled = !state.busy && editedComputerName.isNotBlank(), onClick = {
                            model.rename(computer, editedComputerName)
                            renaming = null
                        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
                    }
                    if (computerSwitcher) ComputerSwitcher(state,
                        onClose = { computerSwitcher = false },
                        onSelect = { computer -> computerSwitcher = false; modelPicker = false; tab = 0; model.connect(computer) },
                        onSettings = { computerSwitcher = false; settings = true })
                    forgetting?.let { computer ->
                        AlertDialog(onDismissRequest = { forgetting = null }, title = { Text("Remove ${computer.name}?") }, text = { Text("You will need a new pairing QR to reconnect. Your conversations stay on the computer.") }, confirmButton = { TextButton(onClick = { model.forget(computer); forgetting = null }) { Text("Remove computer") } }, dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Keep computer") } })
                    }

                    if (pairing) AlertDialog(onDismissRequest = { pairing = false; link = "" }, title = { Text("Connect your computer") }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Run opencode pair on the PC. For Tailscale, connect its VPN and use the PC’s Tailscale IP if the link points to Wi-Fi.")
                            OutlinedTextField(name, { name = it }, label = { Text("Computer name (optional)") })
                            OutlinedTextField(link, { link = it; pairingError = null }, label = { Text("Pairing link (HTTP or HTTPS)") }, isError = pairingError != null)
                            pairingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = sameWifi, onCheckedChange = { sameWifi = it })
                                Text("Use a different Wi-Fi or Tailscale IP")
                            }
                            if (sameWifi) OutlinedTextField(computerIp, { computerIp = it; pairingError = null }, label = { Text("Computer IP") }, placeholder = { Text("192.168.1.20") }, singleLine = true)
                            OutlinedButton(onClick = {
                                scanQr()
                            }) { Text("Scan QR code") }
                            Text("Pair once. This computer is saved securely for next time.", style = MaterialTheme.typography.bodySmall)
                        }
                    }, confirmButton = { Button(enabled = !state.busy && link.isNotBlank(), onClick = {
                        reviewLink()
                    }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { pairing = false; link = "" }) { Text("Cancel") } })
                    if (lanAddress) AlertDialog(
                        onDismissRequest = { lanAddress = false; pairing = true },
                        title = { Text("Choose the computer IP") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("The QR points to the computer itself. Enter its Wi-Fi IP for the same network, or its Tailscale IP if both devices are connected to Tailscale.")
                                OutlinedTextField(computerIp, { computerIp = it; lanError = null }, label = { Text("Computer IP") }, placeholder = { Text("192.168.1.20") }, singleLine = true, isError = lanError != null)
                                lanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Text("Find it with ipconfig on Windows, or your network settings. The QR's port and pairing code are kept automatically.", style = MaterialTheme.typography.bodySmall)
                                Text("The server must listen on the LAN interface and its firewall must allow the QR's port.", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = { Button(enabled = computerIp.isNotBlank(), onClick = {
                            try {
                                confirmation = PairingLink.forLan(link, computerIp)
                                lanAddress = false
                            } catch (e: Exception) { lanError = e.message ?: "Invalid computer IP" }
                        }) { Text("Review connection") } },
                        dismissButton = { TextButton(onClick = { lanAddress = false; pairing = true }) { Text("Back") } }
                    )
                    confirmation?.let { target ->
                        AlertDialog(onDismissRequest = { confirmation = null; pairing = true }, title = { Text("Trust this server?") }, text = {
                            Text("${target.origin}\n\n" + if (target.usesVpnHttp) "Tailscale IP. The app sends this HTTP connection only through an active VPN route." else if (target.usesHttp) "Same Wi-Fi connection. HTTP does not encrypt the pairing code, credentials or conversations. Only continue on a network and computer you trust." else "Only connect to a computer you trust. This app will be able to run OpenCode actions on it.")
                        }, confirmButton = { Button(onClick = {
                            model.pair(target.url.toString(), name); confirmation = null; pairing = false; link = ""
                        }) { Text(if (target.usesHttp) "Connect using HTTP" else "Connect") } }, dismissButton = { TextButton(onClick = { confirmation = null; pairing = true }) { Text("Cancel") } })
                    }
                    deletingSession?.let { session ->
                        AlertDialog(onDismissRequest = { deletingSession = null }, title = { Text("Delete conversation?") }, text = {
                            Text("${session.optString("title").ifBlank { "Untitled session" }}\n\nThis deletes the conversation and its history from your computer. This cannot be undone.")
                        }, confirmButton = { Button(enabled = !state.busy, onClick = {
                            model.deleteSession(session)
                            deletingSession = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingSession = null }) { Text("Keep conversation") } })
                    }

                    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("OpenCode") }, text = { Text(error) }, confirmButton = { TextButton(onClick = model::clearError) { Text("OK") } }) }
                }
            }
        }
    }
}
