package com.totalledscarf2.calude

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File

private val Ink = Color(0xFF20201E)
private val Paper = Color(0xFFF8F8F6)
private val Card = Color.White
private val Muted = Color(0xFF777773)
private val Accent = Color(0xFFD97757)
private val Blue = Color(0xFF3878C7)

data class ChatMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())
data class Conversation(val id: String, var title: String, val messages: MutableList<ChatMessage> = mutableListOf(), var projectId: String? = null)
data class Project(val id: String, var name: String, var instructions: String = "")
data class Memory(val id: String, var title: String, var detail: String, var group: String = "Perfil", val updated: Long = System.currentTimeMillis())
data class Artifact(val title: String, val body: String, val time: Long = System.currentTimeMillis())
enum class Page { CHAT, PROJECTS, CODE, ARTIFACTS, SETTINGS, MEMORY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(248, 248, 246)
        window.navigationBarColor = android.graphics.Color.rgb(248, 248, 246)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { CaludeApp() }
    }
}

@Composable
fun CaludeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calude-data", 0) }
    val secure = remember { runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "calude-secrets", key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull() }
    var apiKey by remember { mutableStateOf(secure?.getString("openrouter", "") ?: "") }
    var tavilyKey by remember { mutableStateOf(secure?.getString("tavily", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini") }
    var memoryOn by remember { mutableStateOf(prefs.getBoolean("memory-on", true)) }
    var webOn by remember { mutableStateOf(prefs.getBoolean("web-on", false)) }
    val projects = remember { mutableStateListOf<Project>().apply { addAll(loadProjects(prefs.getString("projects", "[]")!!)) } }
    val conversations = remember { mutableStateListOf<Conversation>().apply { addAll(loadConversations(prefs.getString("chats", "[]")!!)) } }
    val memories = remember { mutableStateListOf<Memory>().apply { addAll(loadMemories(prefs.getString("memories", "[]")!!)) } }
    val artifacts = remember { mutableStateListOf<Artifact>().apply { addAll(loadArtifacts(prefs.getString("artifacts", "[]")!!)) } }
    var page by remember { mutableStateOf(Page.CHAT) }
    var selectedChat by remember { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showMemoryEdit by remember { mutableStateOf<Memory?>(null) }
    var toast by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val chat = conversations.firstOrNull { it.id == selectedChat }
    fun save() {
        prefs.edit().putString("chats", saveConversations(conversations)).putString("projects", saveProjects(projects)).putString("memories", saveMemories(memories)).putString("model", model).putBoolean("memory-on", memoryOn).putBoolean("web-on", webOn).apply()
    }
    fun newChat(project: String? = null) {
        val c = Conversation(java.util.UUID.randomUUID().toString(), "Nuevo chat", projectId = project)
        conversations.add(0, c); selectedChat = c.id; page = Page.CHAT; save()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = runCatching { context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> val i = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && i >= 0) cursor.getString(i) else "Archivo adjunto" } }.getOrNull() ?: "Archivo adjunto"
            attachment = name to uri.toString()
            toast = "Adjunto listo: $name"
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) runCatching {
            val file = File(context.cacheDir, "calude-camera-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            attachment = "Foto de cámara" to Uri.fromFile(file).toString()
            toast = "Foto lista para adjuntar"
        }.onFailure { toast = "No pude guardar la foto" }
    }
    val speechPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) composer = listOf(composer, words).filter { it.isNotBlank() }.joinToString(" ")
    }
    fun startVoiceInput() {
        runCatching { speechPicker.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL").putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Calude")) }
            .onFailure { toast = "El reconocimiento de voz no está disponible" }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Paper, surface = Card, onSurface = Ink)) {
        Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (page != Page.MEMORY) TopBar(page, onMenu = { drawer = true }, onSettings = { page = Page.SETTINGS }, onModel = { showModel = true }, model = model)
                when (page) {
                    Page.CHAT -> ChatPage(chat, composer, { composer = it }, busy, attachment, { attachment = null }, ::startVoiceInput,
                        onSend = {
                            if (it.isBlank() || busy) return@ChatPage
                            if (apiKey.isBlank()) { page = Page.SETTINGS; toast = "Agrega tu clave de OpenRouter en Configuración"; return@ChatPage }
                            val c = chat ?: Conversation(java.util.UUID.randomUUID().toString(), "Nuevo chat").also { conversations.add(0, it); selectedChat = it.id }
                            val prompt = it
                            val attached = attachment
                            c.messages.add(ChatMessage("user", prompt + (attached?.let { "\n\n[Archivo adjunto: ${it.first}]" } ?: "")))
                            if (c.title == "Nuevo chat") c.title = prompt.take(48).ifBlank { "Nuevo chat" }
                            composer = ""; attachment = null; busy = true; save()
                            scope.launch {
                                try {
                                    val searchText = if (webOn && tavilyKey.isNotBlank()) tavilySearch(tavilyKey, prompt) else ""
                                    val enriched = if (attached != null) c.messages.toMutableList().also { list ->
                                        val fileContent = readAttachment(context, attached.second)
                                        if (fileContent.isNotBlank()) list[list.lastIndex] = list.last().copy(text = list.last().text + "\n\n$ATTACHMENT_MARKER$fileContent")
                                    } else c.messages
                                    val answer = callOpenRouter(apiKey, model, enriched, memories.toList().takeIf { memoryOn } ?: emptyList(), searchText, projects.firstOrNull { it.id == c.projectId }?.instructions ?: "")
                                    c.messages.add(ChatMessage("assistant", answer))
                                    if (memoryOn) extractMemories(prompt, memories)
                                    if (answer.contains("```") || answer.length > 700) artifacts.add(0, Artifact(c.title, answer))
                                    prefs.edit().putString("artifacts", saveArtifacts(artifacts)).apply()
                                    save()
                                } catch (e: Exception) { c.messages.add(ChatMessage("assistant", "No pude completar la respuesta. ${e.message ?: "Revisa tu conexión y configuración."}")); save() }
                                finally { busy = false }
                            }
                        }, onAdd = { showAdd = true }, onModel = { showModel = true }, onNew = { newChat() }, onSaveArtifact = { m -> artifacts.add(0, Artifact("Respuesta guardada", m.text)); toast = "Guardado en Artefactos" }, modifier = Modifier.weight(1f))
                    Page.PROJECTS -> ProjectsPage(projects, conversations, search, { search = it }, { showProjectDialog = true }, { p -> newChat(p.id) }, { p -> projects.remove(p); save() }, Modifier.weight(1f))
                    Page.CODE -> CodePage(model, { page = Page.SETTINGS }, Modifier.weight(1f))
                    Page.ARTIFACTS -> ArtifactsPage(artifacts, search, { search = it }, Modifier.weight(1f))
                    Page.SETTINGS -> SettingsPage(apiKey, { apiKey = it }, tavilyKey, { tavilyKey = it }, model, { model = it }, webOn, { webOn = it; save() }, memoryOn, { memoryOn = it; save() }, onSave = {
                        runCatching { secure?.edit()?.putString("openrouter", apiKey?.trim())?.putString("tavily", tavilyKey?.trim())?.apply() }
                        save()
                        toast = if (secure == null) "No se pudo abrir el almacén seguro en este dispositivo" else "Configuración guardada de forma cifrada"
                    }, onMemories = { page = Page.MEMORY }, modifier = Modifier.weight(1f))
                    Page.MEMORY -> MemoryPage(memories, { showMemoryEdit = it }, { m -> memories.remove(m); save() }, { page = Page.SETTINGS }, Modifier.weight(1f))
                }
                if (page != Page.SETTINGS && page != Page.MEMORY) BottomBar(page, { page = it })
            }
        }
        if (drawer) ModalNavigationDrawerContent(conversations, { selectedChat = it; page = Page.CHAT; drawer = false }, { newChat(); drawer = false }, { page = it; drawer = false }, { id -> conversations.removeAll { it.id == id }; save() }, { drawer = false })
        if (showAdd) AddSheet(webOn, { webOn = it; save() }, memoryOn, { memoryOn = it; save() }, { filePicker.launch(arrayOf("*/*")); showAdd = false }, { filePicker.launch(arrayOf("image/*")); showAdd = false }, { cameraPicker.launch(null); showAdd = false }, { showAdd = false }, { page = Page.PROJECTS; showAdd = false })
        if (showModel) ModelDialog(model, { model = it; save() }, { showModel = false })
        if (showProjectDialog) CreateProjectDialog({ name, instruction -> projects.add(0, Project(java.util.UUID.randomUUID().toString(), name, instruction)); save(); showProjectDialog = false }, { showProjectDialog = false })
        if (showMemoryEdit != null) EditMemoryDialog(showMemoryEdit!!, { title, body -> showMemoryEdit!!.title = title; showMemoryEdit!!.detail = body; save(); showMemoryEdit = null }, { showMemoryEdit = null })
        if (toast.isNotBlank()) LaunchedEffect(toast) { android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_SHORT).show(); kotlinx.coroutines.delay(2400); toast = "" }
    }
}

@Composable private fun TopBar(page: Page, onMenu: () -> Unit, onSettings: () -> Unit, onModel: () -> Unit, model: String) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, null, tint = Ink) }
        if (page == Page.CHAT) TextButton(onClick = onModel) { Text(model.substringAfterLast('/'), color = Ink, fontWeight = FontWeight.Medium); Text("⌄", color = Muted, modifier = Modifier.padding(start = 5.dp)) }
        else Text(when (page) { Page.PROJECTS -> "Proyectos"; Page.CODE -> "Code"; Page.ARTIFACTS -> "Artefactos"; Page.SETTINGS -> "Configuración"; else -> "Calude" }, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, null, tint = Ink) }
    }
}

@Composable private fun BottomBar(page: Page, select: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(32.dp)).background(Card).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        val items = listOf(Page.CHAT to (Icons.Outlined.ChatBubbleOutline to "Chat"), Page.PROJECTS to (Icons.Outlined.Archive to "Proyectos"), Page.CODE to (Icons.Outlined.Code to "Code"), Page.ARTIFACTS to (Icons.Outlined.Widgets to "Artefactos"))
        items.forEach { (p, v) -> Column(Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(if (page == p) Color(0xFFEAEAE8) else Color.Transparent).clickable { select(p) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(v.first, null, tint = Ink, modifier = Modifier.size(23.dp)); Text(v.second, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink) } }
    }
}

@Composable private fun ChatPage(chat: Conversation?, input: String, onInput: (String) -> Unit, busy: Boolean, attachment: Pair<String, String>?, removeAttachment: () -> Unit, onVoice: () -> Unit, onSend: (String) -> Unit, onAdd: () -> Unit, onModel: () -> Unit, onNew: () -> Unit, onSaveArtifact: (ChatMessage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        if (chat == null || chat.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("¿Qué tienes en mente?", fontSize = 29.sp, fontWeight = FontWeight.Medium, color = Ink); Text("Pregunta, escribe o explora una idea.", color = Muted, modifier = Modifier.padding(top = 8.dp)) } }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(chat.messages) { m ->
                if (m.role == "user") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Text(m.text, Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFEFEEEA)).padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 17.sp, color = Ink) }
                else Column(Modifier.fillMaxWidth()) { Text(m.text, fontSize = 17.sp, color = Ink, lineHeight = 26.sp); Text("Copiar   ·   Guardar en artefactos", Modifier.clickable { onSaveArtifact(m) }.padding(top = 9.dp), fontSize = 12.sp, color = Muted) }
            }
            if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) { PaintSpinner(34.dp); Text("A darle vueltas", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(start = 12.dp)) } }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)).background(Card)) {
            if (attachment != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AttachFile, null, tint = Muted); Text(attachment.first, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted); Text("×", Modifier.clickable { removeAttachment() }.padding(horizontal = 12.dp), fontSize = 22.sp) }
            Row(Modifier.fillMaxWidth().padding(7.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAdd, modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF1F0ED))) { Icon(Icons.Outlined.Add, "Agregar al chat", tint = Ink) }
                TextField(value = input, onValueChange = onInput, placeholder = { Text("Responde a Calude", color = Muted) }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), maxLines = 5)
                if (input.isNotBlank() && !busy) IconButton(onClick = { onSend(input) }, modifier = Modifier.size(46.dp).clip(CircleShape).background(Ink)) { Text("↑", fontSize = 23.sp, color = Color.White) }
                else IconButton(onClick = onVoice, modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF1F0ED))) { Icon(Icons.Outlined.Mic, "Dictar", tint = Ink) }
            }
        }
    }
}

@Composable private fun PaintSpinner(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "paint-spin")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
    Canvas(Modifier.size(size).rotate(rotation)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply { moveTo(w*.48f,h*.04f); cubicTo(w*.58f,h*.01f,w*.57f,h*.26f,w*.65f,h*.29f); cubicTo(w*.83f,h*.17f,w*.91f,h*.25f,w*.75f,h*.40f); cubicTo(w*.98f,h*.42f,w*.98f,h*.56f,w*.74f,h*.56f); cubicTo(w*.92f,h*.76f,w*.82f,h*.85f,w*.65f,h*.65f); cubicTo(w*.64f,h*.94f,w*.49f,h*.99f,w*.47f,h*.69f); cubicTo(w*.28f,h*.92f,w*.17f,h*.84f,w*.36f,h*.62f); cubicTo(w*.04f,h*.65f,w*.01f,h*.49f,w*.31f,h*.47f); cubicTo(w*.09f,h*.30f,w*.18f,h*.18f,w*.39f,h*.39f); cubicTo(w*.38f,h*.17f,w*.36f,h*.06f,w*.48f,h*.04f); close() }
        drawPath(p, Brush.linearGradient(listOf(Color(0xFFEA9B7B), Accent, Color(0xFFB8583E)), start = Offset(0f, 0f), end = Offset(w, h)))
        drawPath(p, Color.White.copy(alpha = .32f), style = Stroke(width = w*.045f))
        drawOval(Color.Black.copy(alpha = .10f), Offset(w*.40f,h*.38f), Size(w*.23f,h*.25f))
    }
}

@Composable private fun ProjectsPage(projects: List<Project>, chats: List<Conversation>, search: String, onSearch: (String) -> Unit, create: () -> Unit, open: (Project) -> Unit, delete: (Project) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("Buscar proyectos") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(22.dp), singleLine = true)
        if (projects.isEmpty()) EmptyState("Tus proyectos empiezan aquí", "Agrupa chats e instrucciones para seguir trabajando en un mismo tema.", Icons.Outlined.Archive, Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { items(projects.filter { it.name.contains(search, true) }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Card).clickable { open(p) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.FolderOpen, null, tint = Accent); Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Medium); Text("${chats.count { it.projectId == p.id }} chats", color = Muted, fontSize = 13.sp) }; IconButton(onClick = { delete(p) }) { Icon(Icons.Outlined.DeleteOutline, null, tint = Muted) } }
        } }
        Button(onClick = create, Modifier.align(Alignment.End).padding(vertical = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(28.dp)) { Icon(Icons.Outlined.Add, null); Text("Nuevo proyecto", modifier = Modifier.padding(start = 6.dp)) }
    }
}

@Composable private fun CodePage(model: String, settings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Code, null, Modifier.size(46.dp), tint = Accent); Text("Trabaja con código", fontSize = 28.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp)); Text("Pídele a Calude que escriba, explique y revise código en una conversación. Las respuestas con bloques de código se guardan en Artefactos.", color = Muted, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(vertical = 14.dp)); Text("Modelo actual: ${model.substringAfterLast('/')}", color = Ink); OutlinedButton(onClick = settings, modifier = Modifier.padding(top = 18.dp)) { Text("Configurar conexión") }
    }
}

@Composable private fun ArtifactsPage(artifacts: List<Artifact>, search: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("Buscar artefactos") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(22.dp), singleLine = true)
        if (artifacts.isEmpty()) EmptyState("Todavía no hay artefactos", "Guarda una respuesta del chat para encontrarla aquí.", Icons.Outlined.Widgets, Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) { items(artifacts.filter { it.title.contains(search, true) || it.body.contains(search, true) }) { a -> Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text(a.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(a.body, maxLines = 5, overflow = TextOverflow.Ellipsis, color = Muted, modifier = Modifier.padding(top = 8.dp)) } } } }
    }
}

@Composable private fun SettingsPage(apiKey: String, setApiKey: (String) -> Unit, tavily: String, setTavily: (String) -> Unit, model: String, setModel: (String) -> Unit, web: Boolean, setWeb: (Boolean) -> Unit, memory: Boolean, setMemory: (Boolean) -> Unit, onSave: () -> Unit, onMemories: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Conexión", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text("OpenRouter API", fontWeight = FontWeight.Medium); Text("Tu clave se guarda cifrada en este dispositivo.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)); OutlinedTextField(apiKey, setApiKey, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Clave API") }, placeholder = { Text("sk-or-…") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(model, setModel, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("ID del modelo") }, placeholder = { Text("anthropic/claude-sonnet-4") }, singleLine = true); Text("Usa el ID de cualquier modelo disponible en OpenRouter.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) } }
        Card(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text("Búsqueda web", fontWeight = FontWeight.Medium); OutlinedTextField(tavily, setTavily, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Clave API de Tavily") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); SettingToggle("Activar búsqueda web", web, setWeb) } }
        Card(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SettingToggle("Memoria automática", memory, setMemory); HorizontalDivider(); Row(Modifier.fillMaxWidth().clickable { onMemories() }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.AutoAwesome, null, tint = Accent); Text("Archivos de memoria", Modifier.weight(1f).padding(start = 12.dp)); Text("›", fontSize = 24.sp, color = Muted) } } }
        Button(onClick = onSave, Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(18.dp)) { Text("Guardar configuración") }
    }
}

@Composable private fun SettingToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue)) } }

@Composable private fun MemoryPage(memories: List<Memory>, edit: (Memory) -> Unit, delete: (Memory) -> Unit, back: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("‹", Modifier.clickable { back() }.padding(horizontal = 12.dp), fontSize = 34.sp); Text("Archivos de memoria", fontSize = 21.sp, fontWeight = FontWeight.Medium) }
        if (memories.isEmpty()) EmptyState("Aún no hay recuerdos guardados", "Al conversar, Calude puede guardar preferencias y datos que quieras reutilizar.", Icons.Outlined.AutoAwesome, Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            memories.groupBy { it.group }.forEach { (group, items) -> item { Text(group, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp, top = 15.dp, bottom = 8.dp)) }; items(items) { m -> Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(Card).clickable { edit(m) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(m.title, fontSize = 18.sp); Text("Última actualización: ${date(m.updated)}", color = Muted, modifier = Modifier.padding(top = 4.dp)); Text(m.detail, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }; IconButton(onClick = { delete(m) }) { Icon(Icons.Outlined.DeleteOutline, null, tint = Muted) } } } }
        }
    }
}

@Composable private fun EmptyState(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) { Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, Modifier.size(38.dp), tint = Muted); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp)); Text(desc, color = Muted, modifier = Modifier.padding(top = 6.dp), fontSize = 14.sp) } }

@Composable private fun ModalNavigationDrawerContent(chats: List<Conversation>, select: (String) -> Unit, new: () -> Unit, navigate: (Page) -> Unit, delete: (String) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Calude") }, text = { Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        Text("Nuevo chat  +", Modifier.fillMaxWidth().clickable { new() }.padding(vertical = 13.dp), fontWeight = FontWeight.Medium)
        HorizontalDivider()
        listOf(Page.PROJECTS to "Proyectos", Page.CODE to "Code", Page.ARTIFACTS to "Artefactos", Page.SETTINGS to "Configuración").forEach { (p, label) -> Text(label, Modifier.fillMaxWidth().clickable { navigate(p) }.padding(vertical = 11.dp)) }
        HorizontalDivider(Modifier.padding(vertical = 5.dp))
        chats.forEach { c -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(c.title, Modifier.weight(1f).clickable { select(c.id) }.padding(vertical = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { delete(c.id) }) { Icon(Icons.Outlined.DeleteOutline, null, tint = Muted) } } }
    } }, confirmButton = { TextButton(onClick = close) { Text("Cerrar") } })
}

@Composable private fun AddSheet(web: Boolean, setWeb: (Boolean) -> Unit, memory: Boolean, setMemory: (Boolean) -> Unit, files: () -> Unit, photos: () -> Unit, camera: () -> Unit, close: () -> Unit, project: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Agregar al chat") }, text = { Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AddOption("Cámara", Icons.Outlined.PhotoCamera, camera); AddOption("Fotos", Icons.Outlined.Image, photos); AddOption("Archivos", Icons.Outlined.AttachFile, files) }
        Row(Modifier.fillMaxWidth(), Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).clickable { project() }.background(Color(0xFFF7F7F5)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Archive, null); Text("Agregar al proyecto", Modifier.padding(start = 12.dp)) }
        SettingToggle("Búsqueda web", web, setWeb)
        SettingToggle("Memoria", memory, setMemory)
    } }, confirmButton = { TextButton(onClick = close) { Text("Listo") } })
}

@Composable private fun AddOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) { Column(Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F7F5)).clickable { click() }.padding(vertical = 14.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(27.dp)); Text(label, modifier = Modifier.padding(top = 7.dp), fontSize = 13.sp) } }

@Composable private fun ModelDialog(model: String, select: (String) -> Unit, close: () -> Unit) {
    var custom by remember { mutableStateOf(model) }
    AlertDialog(onDismissRequest = close, title = { Text("Selecciona un modelo") }, text = { Column {
        listOf("anthropic/claude-sonnet-4", "anthropic/claude-3.7-sonnet", "openai/gpt-4o-mini", "google/gemini-2.5-flash").forEach { id -> Row(Modifier.fillMaxWidth().clickable { select(id); close() }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(id.substringAfterLast('/'), fontSize = 17.sp); Text(id, fontSize = 12.sp, color = Muted) }; if (id == model) Text("✓", color = Blue, fontSize = 20.sp) } }
        HorizontalDivider(Modifier.padding(vertical = 8.dp)); OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), label = { Text("Otro ID de OpenRouter") }, singleLine = true)
    } }, confirmButton = { TextButton(onClick = { if (custom.isNotBlank()) select(custom.trim()); close() }) { Text("Usar modelo") } }, dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
}

@Composable private fun CreateProjectDialog(create: (String, String) -> Unit, close: () -> Unit) {
    var name by remember { mutableStateOf("") }; var instructions by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Nuevo proyecto") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true); OutlinedTextField(instructions, { instructions = it }, label = { Text("Instrucciones del proyecto") }, modifier = Modifier.padding(top = 8.dp), minLines = 3) } }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name.trim(), instructions.trim()) }) { Text("Crear") } }, dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
}

@Composable private fun EditMemoryDialog(memory: Memory, save: (String, String) -> Unit, close: () -> Unit) {
    var title by remember { mutableStateOf(memory.title) }; var body by remember { mutableStateOf(memory.detail) }
    AlertDialog(onDismissRequest = close, title = { Text("Editar memoria") }, text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Título") }); OutlinedTextField(body, { body = it }, label = { Text("Detalle") }, modifier = Modifier.padding(top = 8.dp), minLines = 3) } }, confirmButton = { TextButton(onClick = { save(title, body) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
}

private suspend fun callOpenRouter(key: String, model: String, messages: List<ChatMessage>, memories: List<Memory>, web: String, instructions: String): String = withContext(Dispatchers.IO) {
    val system = buildString {
        append("Eres Calude, un asistente útil. Responde en el idioma del usuario. Sé claro y directo.")
        if (memories.isNotEmpty()) append("\n\nRecuerdos relevantes del usuario:\n" + memories.joinToString("\n") { "- ${it.title}: ${it.detail}" })
        if (instructions.isNotBlank()) append("\n\nInstrucciones del proyecto:\n$instructions")
        if (web.isNotBlank()) append("\n\nResultados de búsqueda web recientes (cítalos con sus URLs):\n$web")
    }
    val arr = JSONArray().put(JSONObject().put("role", "system").put("content", system))
    messages.takeLast(30).forEach { m ->
        val markerIndex = m.text.indexOf(ATTACHMENT_MARKER)
        if (markerIndex >= 0 && m.role == "user") {
            val before = m.text.substring(0, markerIndex).trim()
            val payload = m.text.substring(markerIndex + ATTACHMENT_MARKER.length)
            if (payload.startsWith("data:image/")) {
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", before.ifBlank { "Describe this image." }))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", payload)))
                arr.put(JSONObject().put("role", m.role).put("content", content))
            } else arr.put(JSONObject().put("role", m.role).put("content", before + "\n\nTexto del archivo adjunto:\n" + payload))
        } else arr.put(JSONObject().put("role", m.role).put("content", m.text))
    }
    val body = JSONObject().put("model", model).put("messages", arr).put("temperature", .7).put("stream", false)
    val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true; setRequestProperty("Authorization", "Bearer $key"); setRequestProperty("Content-Type", "application/json"); setRequestProperty("HTTP-Referer", "https://github.com/Totalledscarf2/Calude"); setRequestProperty("X-Title", "Calude") }
    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    val status = conn.responseCode; val stream = if (status in 200..299) conn.inputStream else conn.errorStream
    val response = stream.bufferedReader().use { it.readText() }; conn.disconnect()
    val json = JSONObject(response)
    if (status !in 200..299) throw IllegalStateException(json.optJSONObject("error")?.optString("message") ?: "OpenRouter HTTP $status")
    json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").ifBlank { "La respuesta llegó vacía." }
}

private suspend fun tavilySearch(key: String, query: String): String = withContext(Dispatchers.IO) {
    val conn = (URL("https://api.tavily.com/search").openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 15000; readTimeout = 25000; doOutput = true; setRequestProperty("Content-Type", "application/json") }
    conn.outputStream.use { it.write(JSONObject().put("api_key", key).put("query", query).put("search_depth", "basic").put("max_results", 5).toString().toByteArray()) }
    val status = conn.responseCode; val text = (if (status in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }; conn.disconnect()
    if (status !in 200..299) throw IllegalStateException("Tavily HTTP $status: ${JSONObject(text).optString("detail", "revisa la clave de Tavily")}")
    JSONObject(text).getJSONArray("results").let { results -> (0 until results.length()).joinToString("\n\n") { i -> results.getJSONObject(i).let { "${it.optString("title")}\n${it.optString("content")}\n${it.optString("url")}" } } }
}

private const val ATTACHMENT_MARKER = "\n\n[[CALUDE_ATTACHMENT]]"

private fun readAttachment(context: android.content.Context, uriString: String): String = runCatching {
    val uri = Uri.parse(uriString)
    val mime = context.contentResolver.getType(uri).orEmpty()
    if (mime.startsWith("image/")) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ""
        "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } else {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ""
        if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("javascript")) bytes.toString(Charsets.UTF_8).take(200_000)
        else "Este tipo de archivo ($mime) no se pudo convertir a texto."
    }
}.getOrDefault("")

private fun extractMemories(text: String, memories: MutableList<Memory>) {
    val patterns = listOf(Regex("(?i)(?:me gusta|me encanta|prefiero|amo|odio|mi serie favorita es|mi canción favorita es|mi gato se llama)\\s+([^.!?\\n]{2,100})"))
    patterns.flatMap { it.findAll(text).toList() }.forEach { match ->
        val detail = match.value.trim().trimEnd('.', '!', '?')
        if (memories.none { it.detail.equals(detail, true) }) memories.add(0, Memory(java.util.UUID.randomUUID().toString(), detail.take(42), detail, "Preferencias"))
    }
}

private fun date(time: Long) = runCatching { DateTimeFormatter.ofPattern("d MMM yyyy").withLocale(java.util.Locale("es", "CL")).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())) }.getOrDefault("Hoy")

private fun loadConversations(raw: String): List<Conversation> = runCatching { val a = JSONArray(raw); (0 until a.length()).map { i -> val o = a.getJSONObject(i); val ms = o.optJSONArray("messages") ?: JSONArray(); Conversation(o.getString("id"), o.optString("title", "Chat"), (0 until ms.length()).map { j -> ms.getJSONObject(j).let { ChatMessage(it.getString("role"), it.getString("text"), it.optLong("time")) } }.toMutableList(), o.optString("projectId").ifBlank { null }) } }.getOrDefault(emptyList())
private fun saveConversations(list: List<Conversation>): String = JSONArray().apply { list.forEach { c -> put(JSONObject().put("id", c.id).put("title", c.title).put("projectId", c.projectId).put("messages", JSONArray().apply { c.messages.forEach { put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time)) } })) } }.toString()
private fun loadProjects(raw: String): List<Project> = runCatching { val a = JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).let { o -> Project(o.getString("id"), o.getString("name"), o.optString("instructions")) } } }.getOrDefault(emptyList())
private fun saveProjects(list: List<Project>) = JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("instructions", it.instructions)) } }.toString()
private fun loadMemories(raw: String): List<Memory> = runCatching { val a = JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).let { o -> Memory(o.getString("id"), o.getString("title"), o.getString("detail"), o.optString("group", "Perfil"), o.optLong("updated")) } } }.getOrDefault(emptyList())
private fun saveMemories(list: List<Memory>) = JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("detail", it.detail).put("group", it.group).put("updated", it.updated)) } }.toString()
private fun loadArtifacts(raw: String): List<Artifact> = runCatching { val a = JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).let { o -> Artifact(o.getString("title"), o.getString("body"), o.optLong("time")) } } }.getOrDefault(emptyList())
private fun saveArtifacts(list: List<Artifact>) = JSONArray().apply { list.forEach { put(JSONObject().put("title", it.title).put("body", it.body).put("time", it.time)) } }.toString()
