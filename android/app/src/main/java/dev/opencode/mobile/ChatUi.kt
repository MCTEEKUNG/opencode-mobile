package dev.opencode.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun MarkdownProse(source: String) {
    val foreground = LocalContentColor.current
    val text = buildAnnotatedString {
        source.trim().lines().forEachIndexed { index, original ->
            if (index > 0) append("\n")
            val heading = Regex("^#{1,6}\\s+").find(original)
            val line = if (heading != null) original.removeRange(heading.range) else original.replace(Regex("^[-*] "), "\u2022 ")
            withStyle(if (heading != null) SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp) else SpanStyle()) {
                val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
                var offset = 0
                pattern.findAll(line).forEach { match ->
                    append(line.substring(offset, match.range.first))
                    if (match.value.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
                    else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = foreground.copy(alpha = .12f))) { append(match.groupValues[2]) }
                    offset = match.range.last + 1
                }
                append(line.substring(offset))
            }
        }
    }
    SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun MessageBody(text: String) {
    val sections = remember(text) { text.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEachIndexed { index, section ->
            if (section.isNotBlank()) {
                if (index % 2 == 1) {
                    val code = section.substringAfter('\n', section).trimEnd()
                    val foreground = LocalContentColor.current
                    Surface(color = foreground.copy(alpha = .08f), contentColor = foreground, shape = RoundedCornerShape(12.dp)) {
                        SelectionContainer { Text(code, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                } else MarkdownProse(section)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: JSONObject, computer: Computer?, directory: String) {
    val mine = message.optString("type") == "user"
    val files = message.chatAttachments()
    val prose = message.chatProse().let { text ->
        if (files.any { it.image }) text.replace(Regex("\\[Image\\s*\\d+\\]", RegexOption.IGNORE_CASE), "").trim() else text
    }
    val reasoning = remember(message) { message.chatReasoning() }
    val clipboard = LocalClipboardManager.current
    val timestamp = message.optJSONObject("time")?.optLong("created", 0) ?: 0
    val time = remember(timestamp) { if (timestamp > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) else "" }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            modifier = Modifier.widthIn(max = if (mine) 500.dp else 600.dp).padding(start = if (mine) 36.dp else 0.dp, end = if (mine) 0.dp else 12.dp),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = .82f),
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (mine) "You" else "OpenCode", style = MaterialTheme.typography.labelMedium)
                files.forEach { file -> AttachmentPreview(file, computer, directory, Modifier.fillMaxWidth()) }
                if (prose.isNotBlank()) MessageBody(prose)
                if (reasoning.isNotBlank()) {
                    var expanded by remember(message.optString("id")) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                        Icon(if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (expanded) "Hide reasoning" else "Show reasoning")
                    }
                    if (expanded) SelectionContainer { Text(reasoning, style = MaterialTheme.typography.bodySmall) }
                }
                if (message.optString("type") == "idle") Text(if (message.optString("outcome") == "interrupted") "Response stopped" else "The response failed. You can try again.")
                if (message.optString("type") == "assistant" && !message.isNull("error")) Text("This response encountered an error. Check the computer for details.")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            message.chatUsage()?.let { usage ->
                Text("${formatTokenCount(usage.tokens)} tokens · ${formatTokensPerSecond(usage.tokensPerSecond)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (prose.isNotBlank()) ActionIcon(AppIcons.Copy, "Copy message", { clipboard.setText(AnnotatedString(prose)) })
        }
    }
}

@Composable
fun ChatTimeline(state: MobileState, onOlder: () -> Unit, modifier: Modifier = Modifier) {
    val messages = state.messages.filter { it.chatVisible() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollBackward }.distinctUntilChanged().collect { (moving, ahead) ->
            if (moving) follow = !ahead
        }
    }
    LaunchedEffect(messages.lastOrNull()?.toString()) {
        if (messages.isNotEmpty() && (firstLoad || follow)) {
            list.scrollToItem(0)
            firstLoad = false
        }
    }
    Box(modifier) {
        LazyColumn(state = list, reverseLayout = true, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What are we building?", style = MaterialTheme.typography.headlineSmall)
                    Text("Ask a question, describe a change, or pick up where you left off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(messages.asReversed(), key = { it.getString("id") }) { ChatBubble(it, state.computer, state.session?.optJSONObject("location")?.optString("directory").orEmpty()) }
            if (state.nextMessages != null) item { ActionIcon(AppIcons.Earlier, "Load earlier messages", { follow = false; onOlder() }, enabled = !state.busy) }
        }
        if (list.canScrollBackward) ActionIcon(AppIcons.Down, "Latest messages", { follow = true; scope.launch { list.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomCenter).size(48.dp), filled = true)
    }
}

@Composable
fun ChatComposer(state: MobileState, onDraft: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, onModels: () -> Unit, onImages: () -> Unit, onFiles: () -> Unit, onRemove: (Int) -> Unit, onMoveProject: () -> Unit, onLoadCatalog: () -> Unit) {
    val sessionId = state.session?.optString("id")
    val draft = state.drafts[sessionId].orEmpty()
    val files = state.attachments[sessionId].orEmpty()
    val connected = state.status == PeerStatus.CONNECTED.label
    val work = state.workingSessions[sessionId].orEmpty()
    val working = WorkKind.RESPONSE in work
    var attachMenu by remember(sessionId) { mutableStateOf(false) }
    var editor by remember(sessionId) { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    var dismissed by remember(sessionId) { mutableStateOf<ComposerToken?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(draft) {
        if (editor.text != draft) editor = TextFieldValue(draft, TextRange(draft.length))
    }
    val token = composerToken(editor.text, editor.selection.end)
        ?.takeIf { editor.selection.collapsed && it != dismissed }
    LaunchedEffect(state.computer?.id, sessionId, token?.symbol, connected, state.missingProject, state.composerCatalogLoaded) {
        if (token != null && connected && !state.missingProject && !state.composerCatalogLoaded &&
            !state.composerLoading && state.composerError == null) onLoadCatalog()
    }
    fun updateEditor(value: TextFieldValue) {
        val changed = editor.text != value.text
        if (value != editor) dismissed = null
        editor = value
        if (changed) onDraft(value.text)
    }
    val selected = state.session?.optJSONObject("model")
    val selectedModel = selected?.let { ref -> state.models.find { it.id == ref.optString("id") && it.provider == ref.optString("providerID") } }
    val isFast = selectedModel?.isFastModel == true
    val variant = (selected?.opt("variant") as? String).orEmpty()
    val fastVariant = variant.equals("fast", ignoreCase = true)
    val selectedName = selectedModel?.let { ModelChoice(it).name }
        ?: selected?.optString("id")?.takeIf { it.isNotBlank() } ?: "Choose model"
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp)) {
            if (state.missingProject) {
                Text("Project folder missing on the PC", Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onMoveProject, enabled = !state.busy) { Text("Change project folder") }
            }
            if (state.importingFiles) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (files.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(6.dp)) {
                items(files.size) { index ->
                    Box(Modifier.width(150.dp)) {
                        AttachmentPreview(files[index], null, "", Modifier.fillMaxWidth())
                        ActionIcon(AppIcons.Close, "Remove ${files[index].name}", { onRemove(index) }, modifier = Modifier.align(Alignment.TopEnd), filled = true)
                    }
                }
            }
            token?.let {
                ComposerSuggestions(it, if (it.symbol == '/') state.commands else state.skills,
                    loading = connected && !state.missingProject && (state.composerLoading ||
                        (!state.composerCatalogLoaded && state.composerError == null)),
                    error = when {
                        !connected -> "Connect to your computer to see this project’s catalog."
                        state.missingProject -> "Choose an existing project folder to load its catalog."
                        else -> state.composerError
                    },
                    onSelect = { entry -> updateEditor(it.replace(editor.text, entry.name)); focusRequester.requestFocus() },
                    onRetry = onLoadCatalog, onClose = { dismissed = it },
                    canRetry = connected && !state.missingProject)
            }
            TextField(value = editor, onValueChange = ::updateEditor, placeholder = { Text("Message OpenCode") },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant, unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ActionIcon(AppIcons.Attach, "Attach image or file", { attachMenu = true }, enabled = !state.busy && !state.importingFiles)
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Photos") }, leadingIcon = { Icon(AppIcons.Image, null) }, onClick = { attachMenu = false; onImages() })
                        DropdownMenuItem(text = { Text("Files") }, leadingIcon = { Icon(AppIcons.Attach, null) }, onClick = { attachMenu = false; onFiles() })
                    }
                }
                TextButton(onClick = onModels, modifier = Modifier.weight(1f), enabled = !state.busy && work.isEmpty(),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(AppIcons.Model, contentDescription = "Choose model", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(selectedName + if (variant.isBlank() || fastVariant) "" else " · $variant",
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isFast || fastVariant) Icon(ModelFastIcon, contentDescription = "Fast mode on", modifier = Modifier.size(18.dp))
                    Icon(AppIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                if (working) ActionIcon(AppIcons.Stop, "Stop response", onStop,
                    enabled = connected && !state.interrupting, modifier = Modifier.size(56.dp), filled = true, iconSize = 26.dp)
                else ActionIcon(AppIcons.Send, "Send message", onSend,
                    enabled = connected && work.isEmpty() && !state.missingProject && !state.busy && !state.importingFiles &&
                        (draft.isNotBlank() || files.isNotEmpty()), modifier = Modifier.size(56.dp), filled = true, iconSize = 26.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatModelPicker(state: MobileState, onClose: () -> Unit, onReload: () -> Unit, onMoveProject: () -> Unit, onChoose: (ChatModel, String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = state.session?.optJSONObject("model")
    val savedKey = selected?.let { "${it.optString("providerID")}/${it.optString("id")}" }
    val savedVariant = (selected?.opt("variant") as? String)?.takeIf(String::isNotBlank)
    var pendingKey by remember(state.session?.optString("id")) { mutableStateOf(savedKey) }
    var pendingVariant by remember(state.session?.optString("id")) { mutableStateOf(savedVariant) }
    val candidate = state.models.find { it.key == pendingKey }
    val effort = pendingVariant?.takeIf { it in candidate?.variants.orEmpty() }
    val catalog = remember(state.models) { state.models.modelChoices() }
    val groups = remember(catalog, query) {
        val search = query.trim()
        catalog.filter { choice ->
            listOfNotNull(choice.main, choice.fastModel).any {
                "${it.name} ${it.id} ${providerLabel(it.provider)} ${it.provider}".contains(search, ignoreCase = true)
            }
        }.groupBy { it.main.provider }.entries.sortedBy { providerLabel(it.key).lowercase(Locale.ROOT) }
    }
    fun select(model: ChatModel, variant: String? = savedVariant?.takeIf { model.key == savedKey }) {
        pendingKey = model.key
        pendingVariant = variant
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Model & effort") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search models or providers") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (state.modelsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.modelsError?.let {
                Text(it)
                if (state.missingProject) TextButton(onClick = onMoveProject, enabled = !state.busy) { Text("Change project folder") }
                else TextButton(onClick = onReload) { Text("Try again") }
            }
            if (!state.modelsLoading && state.modelsError == null && groups.isEmpty()) Text(if (state.models.isEmpty()) "No enabled models. Configure a provider on your computer, then refresh." else "No matching models.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                groups.forEach { (provider, choices) ->
                    stickyHeader(key = "provider:$provider") {
                        Text(providerLabel(provider), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(AlertDialogDefaults.containerColor)
                                .padding(start = 12.dp, top = 10.dp, bottom = 6.dp).semantics { heading() })
                    }
                    items(choices, key = { it.main.key }) { choice ->
                        val active = choice.contains(pendingKey)
                        val rowModel = candidate?.takeIf { active } ?: choice.main
                        val rowVariant = effort.takeIf { active }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                    selected = active, role = Role.RadioButton, enabled = !state.busy,
                                    onClick = { if (!active) select(choice.main) },
                                ).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(choice.name, style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (active) Icon(AppIcons.Check, contentDescription = "Selected model", modifier = Modifier.size(18.dp))
                                }
                                if (choice.supportsFast(rowModel)) {
                                    FastModelToggle(checked = choice.isFast(rowModel, rowVariant), enabled = !state.busy) {
                                        val (target, targetVariant) = choice.toggleFast(rowModel, rowVariant)
                                        select(target, targetVariant)
                                    }
                                } else if (rowModel.isFastModel) {
                                    Icon(ModelFastIcon, contentDescription = "Fast model", modifier = Modifier.padding(horizontal = 12.dp).size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            candidate?.let { model ->
                val variants = model.variants.filterNot { it == model.fastVariant }
                if (variants.isNotEmpty()) {
                    Text("Effort", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = effort == null, onClick = { pendingVariant = null }, label = { Text("Default") }) }
                        items(variants, key = { it }) { variant ->
                            FilterChip(selected = effort == variant, onClick = { pendingVariant = variant }, label = { Text(variant.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        ActionIcon(AppIcons.Check, "Apply model and effort", { candidate?.let { onChoose(it, effort) } }, enabled = candidate != null && !state.busy && !state.modelsLoading, filled = true)
    }, dismissButton = {
        Row {
            ActionIcon(AppIcons.Refresh, "Refresh models", onReload, enabled = !state.modelsLoading)
            ActionIcon(AppIcons.Close, "Close model picker", onClose)
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastModelToggle(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val label = if (checked) "Turn off Fast mode" else "Turn on Fast mode"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconToggleButton(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled,
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            )) {
            Icon(ModelFastIcon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
